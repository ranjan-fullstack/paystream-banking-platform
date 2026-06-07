package com.flipkartclone.productservice.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkartclone.productservice.client.InventoryClient;
import com.flipkartclone.productservice.dto.*;
import com.flipkartclone.productservice.exception.NotFoundException;
import com.flipkartclone.productservice.mapper.ProductMapper;
import com.flipkartclone.productservice.model.*;
import com.flipkartclone.productservice.repository.*;
import com.flipkartclone.productservice.service.ImageStorageService;
import com.flipkartclone.productservice.service.InventoryServiceProxy;
import com.flipkartclone.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepo;
    private final CategoryRepository categoryRepo;
    private final ReviewRepository reviewRepo;
    private final ImageStorageService imageStorageService;
    private final ProductMapper productMapper;
    private final InventoryServiceProxy inventoryServiceProxy;
    private final InventoryClient inventoryClient;

    // Outbox
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    // ---------------- ADD PRODUCT ----------------
    @Override
    @Transactional
    @CacheEvict(value = {"products", "products:all"}, allEntries = true, beforeInvocation = false)
    public ProductResponse addProduct(ProductRequest request) {

        Product product = productMapper.toEntity(request);

        Category category = categoryRepo.findById(request.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found"));

        product.setCategory(category);
        product.setDeleted(false);

        Product saved = productRepo.save(product);

        // ✅ OUTBOX EVENT
        saveOutboxEvent("PRODUCT", saved.getId(), "PRODUCT_CREATED", saved);

        return productMapper.toResponse(saved);
    }

    // ---------------- GET ALL (SOFT DELETE SAFE) ----------------
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products:all")
    public List<ProductResponse> getAllProducts() {
        System.out.println("🔥 FETCHING FROM DATABASE");
        return productRepo.findByDeletedFalse()
                .stream()
                .map(productMapper::toResponse)
                .collect(Collectors.toList());
    }

    // ---------------- GET ALL PAGED (V2) ----------------
    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAllProductsPaged(Pageable pageable) {
        return productRepo.findByDeletedFalse(pageable)
                .map(productMapper::toResponse);
    }

    // ---------------- GET BY ID ----------------
    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProductById(Long id) {

        Product product = productRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        boolean inStock = inventoryServiceProxy.checkStock(id).join();

        ProductResponse response = productMapper.toResponse(product);
        response.setInStock(inStock);
        return response;
    }

    // ---------------- UPDATE (OPTIMISTIC LOCKING) ----------------
    @Override
    @Transactional
    @CacheEvict(value = {"products", "products:all"}, key = "#id", allEntries = true)
    public ProductResponse updateProduct(Long id, ProductRequest req) {

        Product product = productRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        Category category = categoryRepo.findById(req.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found"));

        product.setTitle(req.getTitle());
        product.setDescription(req.getDescription());
        product.setPrice(req.getPrice());
        product.setDiscount(req.getDiscount());
        product.setBrand(req.getBrand());
        product.setStock(req.getStock());
        product.setImageUrl(req.getImageUrl());
        product.setRating(req.getRating());
        product.setCategory(category);

        Product updated = productRepo.save(product);

        // ✅ OUTBOX EVENT
        saveOutboxEvent("PRODUCT", updated.getId(), "PRODUCT_UPDATED", updated);

        return productMapper.toResponse(updated);
    }

    // ---------------- SOFT DELETE ----------------
    @Override
    @Transactional
    @CacheEvict(value = {"products", "products:all"}, key = "#id", allEntries = true)
    public void deleteProduct(Long id) {

        Product product = productRepo.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        product.setDeleted(true);
        productRepo.save(product);

        // ✅ OUTBOX EVENT
        saveOutboxEvent("PRODUCT", id, "PRODUCT_DELETED", product);
    }

    // ---------------- IMAGE UPLOAD ----------------
    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public String uploadProductImage(Long productId, MultipartFile file) {

        Product product = productRepo.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        String imageUrl = imageStorageService.saveImage(file);

        product.setImageUrl(imageUrl);
        productRepo.save(product);

        return imageUrl;
    }

    // ---------------- VARIANT ----------------
    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public ProductResponse addVariant(Long productId, VariantRequest request) {

        Product product = productRepo.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        ProductVariant variant = ProductVariant.builder()
                .variantName(request.getVariantName())
                .variantValue(request.getVariantValue())
                .additionalPrice(request.getAdditionalPrice())
                .product(product)
                .build();

        if (product.getVariants() == null) {
            product.setVariants(new ArrayList<>());
        }

        product.getVariants().add(variant);

        productRepo.save(product);

        return productMapper.toResponse(product);
    }

    // ---------------- REVIEW (OPTIMIZED) ----------------
    @Override
    @Transactional
    @CacheEvict(value = "products", key = "#productId")
    public ReviewResponse addReview(Long productId, ReviewRequest request) {

        Product product = productRepo.findByIdAndDeletedFalse(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

        Review review = Review.builder()
                .userId(request.getUserId())
                .rating(request.getRating())
                .comment(request.getComment())
                .product(product)
                .build();

        reviewRepo.save(review);

        // ✅ OPTIMIZED QUERY
        double avgRating = reviewRepo.calculateAverageRating(productId);
        product.setRating(avgRating);
        productRepo.save(product);

        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }

    // ---------------- OUTBOX HELPER ----------------
    private void saveOutboxEvent(String type, Long id, String event, Product product) {
        try {

            ProductCreatedEvent eventPayload =
                    ProductCreatedEvent.builder()
                            .productId(product.getId())
                            .title(product.getTitle())
                            .brand(product.getBrand())
                            .price(product.getPrice())
                            .categoryId(product.getCategory().getId())
                            .build();

            String payload = objectMapper.writeValueAsString(eventPayload);

            outboxRepository.save(
                    OutboxEvent.builder()
                            .aggregateType(type)
                            .aggregateId(id)
                            .eventType(event)
                            .payload(payload)
                            .status("NEW")
                            .createdAt(LocalDateTime.now())
                            .build()
            );
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }
}
