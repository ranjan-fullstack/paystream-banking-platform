package com.flipkartclone.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkartclone.productservice.dto.ProductRequest;
import com.flipkartclone.productservice.dto.ProductResponse;
import com.flipkartclone.productservice.mapper.ProductMapper;
import com.flipkartclone.productservice.model.Category;
import com.flipkartclone.productservice.model.Product;
import com.flipkartclone.productservice.model.OutboxEvent;
import com.flipkartclone.productservice.repository.*;
import com.flipkartclone.productservice.service.ImageStorageService;
import com.flipkartclone.productservice.service.InventoryServiceProxy;
import com.flipkartclone.productservice.client.InventoryClient;
import com.flipkartclone.productservice.service.impl.ProductServiceImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepo;

    @Mock
    private CategoryRepository categoryRepo;

    @Mock
    private ReviewRepository reviewRepo;

    @Mock
    private ImageStorageService imageStorageService;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private InventoryServiceProxy inventoryServiceProxy;

    @Mock
    private InventoryClient inventoryClient;

    @Mock
    private OutboxRepository outboxRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ProductServiceImpl service;

    @Test
    void shouldCreateProduct() {
        ProductRequest request = new ProductRequest();
        request.setTitle("TV");
        request.setDescription("Smart TV");
        request.setPrice(50000.0);
        request.setBrand("Samsung");
        request.setStock(10);
        request.setCategoryId(1L);

        Category category = Category.builder()
                .id(1L)
                .name("Electronics")
                .build();

        Product productToSave = Product.builder()
                .title("TV")
                .description("Smart TV")
                .price(50000.0)
                .brand("Samsung")
                .stock(10)
                .build();

        Product savedProduct = Product.builder()
                .id(1L)
                .title("TV")
                .description("Smart TV")
                .price(50000.0)
                .brand("Samsung")
                .stock(10)
                .category(category)
                .build();

        ProductResponse expectedResponse = new ProductResponse();
        expectedResponse.setId(1L);
        expectedResponse.setTitle("TV");
        expectedResponse.setDescription("Smart TV");
        expectedResponse.setPrice(50000.0);
        expectedResponse.setBrand("Samsung");

        when(productMapper.toEntity(request)).thenReturn(productToSave);
        when(categoryRepo.findById(1L)).thenReturn(Optional.of(category));
        when(productRepo.save(any(Product.class))).thenReturn(savedProduct);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());
        when(productMapper.toResponse(savedProduct)).thenReturn(expectedResponse);

        ProductResponse saved = service.addProduct(request);

        assertNotNull(saved);
        assertEquals("TV", saved.getTitle());
        verify(productRepo, times(1)).save(any(Product.class));
        verify(outboxRepository, times(1)).save(any(OutboxEvent.class));
    }

    @Test
    void shouldGetProductById() {
        Product product = Product.builder()
                .id(1L)
                .title("Laptop")
                .description("Gaming Laptop")
                .price(80000.0)
                .brand("Dell")
                .stock(5)
                .deleted(false)
                .build();

        ProductResponse response = new ProductResponse();
        response.setId(1L);
        response.setTitle("Laptop");
        response.setBrand("Dell");

        when(productRepo.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(product));
        when(inventoryServiceProxy.checkStock(1L)).thenReturn(CompletableFuture.completedFuture(true));
        when(productMapper.toResponse(product)).thenReturn(response);

        ProductResponse found = service.getProductById(1L);

        assertNotNull(found);
        assertEquals("Laptop", found.getTitle());
        assertTrue(found.isInStock());
        verify(productRepo, times(1)).findByIdAndDeletedFalse(1L);
    }
}