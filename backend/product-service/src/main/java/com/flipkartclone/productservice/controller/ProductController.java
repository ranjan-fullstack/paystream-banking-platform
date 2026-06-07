package com.flipkartclone.productservice.controller;

import com.flipkartclone.productservice.dto.*;
import com.flipkartclone.productservice.model.Product;
import com.flipkartclone.productservice.repository.ProductRepository;
import com.flipkartclone.productservice.service.ImageStorageService;
import com.flipkartclone.productservice.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product APIs", description = "Product Management APIs")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ImageStorageService imageStorageService;

    @Operation(summary = "Create a new product")
    @PostMapping
    public ProductResponse addProduct(@Valid @RequestBody ProductRequest request) {
        return productService.addProduct(request);
    }

    @Deprecated
    @Operation(
        summary = "Get all products (deprecated — use GET /api/v2/products for paginated response)",
        deprecated = true
    )
    @GetMapping
    public List<ProductResponse> getAll() {
        return productService.getAllProducts();
    }

    @Operation(summary = "Get product by ID")
    @GetMapping("/{id}")
    public ProductResponse getById(
            @Parameter(description = "Product ID") @PathVariable("id") Long id) {
        return productService.getProductById(id);
    }

    @Operation(summary = "Update an existing product")
    @PutMapping("/{id}")
    public ProductResponse updateProduct(
            @Parameter(description = "Product ID") @PathVariable("id") Long id,
            @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @Operation(summary = "Soft-delete a product by ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(
            @Parameter(description = "Product ID") @PathVariable("id") Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok("Product deleted successfully");
    }

    @Operation(summary = "Upload product image — compressed and stored in S3, returns CloudFront URL")
    @PostMapping("/{productId}/image")
    public ResponseEntity<String> uploadImage(
            @Parameter(description = "Product ID") @PathVariable("productId") Long productId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(productService.uploadProductImage(productId, file));
    }

    @Operation(summary = "Get a 15-minute presigned URL for a private S3 object")
    @GetMapping("/images/presigned-url")
    public ResponseEntity<String> getPresignedUrl(
            @Parameter(description = "S3 object key, e.g. products/uuid_filename.jpg")
            @RequestParam("key") String objectKey) {
        return ResponseEntity.ok(imageStorageService.generatePresignedUrl(objectKey));
    }

    @Operation(summary = "Add a variant (size, color, etc.) to a product")
    @PostMapping("/{productId}/variants")
    public ResponseEntity<?> addVariant(
            @Parameter(description = "Product ID") @PathVariable("productId") Long productId,
            @Valid @RequestBody VariantRequest request) {
        return ResponseEntity.ok(productService.addVariant(productId, request));
    }

    @Operation(summary = "Add a review to a product")
    @PostMapping("/{productId}/review")
    public ResponseEntity<ReviewResponse> addReview(
            @Parameter(description = "Product ID") @PathVariable("productId") Long productId,
            @Valid @RequestBody ReviewRequest request) {
        return ResponseEntity.ok(productService.addReview(productId, request));
    }
}
