package com.flipkartclone.productservice.service;

import com.flipkartclone.productservice.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {

    ProductResponse addProduct(ProductRequest request);
    List<ProductResponse> getAllProducts();
    Page<ProductResponse> getAllProductsPaged(Pageable pageable);
    ProductResponse getProductById(Long id);
    ProductResponse updateProduct(Long id, ProductRequest request);
    void deleteProduct(Long id);
    String uploadProductImage(Long productId, MultipartFile file);
    ProductResponse addVariant(Long productId, VariantRequest request);
    ReviewResponse addReview(Long productId, ReviewRequest request);





}
