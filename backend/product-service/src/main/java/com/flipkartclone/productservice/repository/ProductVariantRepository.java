package com.flipkartclone.productservice.repository;

import com.flipkartclone.productservice.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {
}
