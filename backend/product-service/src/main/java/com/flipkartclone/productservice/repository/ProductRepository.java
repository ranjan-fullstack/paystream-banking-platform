package com.flipkartclone.productservice.repository;

import com.flipkartclone.productservice.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // JOIN FETCH category + variants in one query — prevents N+1
    @EntityGraph(attributePaths = {"category", "variants"})
    List<Product> findByDeletedFalse();

    // Paginated version — same eager fetch strategy
    @EntityGraph(attributePaths = {"category", "variants"})
    Page<Product> findByDeletedFalse(Pageable pageable);

    // Detail view — also fetch reviews since single product page shows them
    @EntityGraph(attributePaths = {"category", "variants", "reviews"})
    Optional<Product> findByIdAndDeletedFalse(Long id);
}
