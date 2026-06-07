package com.flipkartclone.productservice.repository;

import com.flipkartclone.productservice.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    @Query("""
        SELECT COALESCE(AVG(r.rating), 0)
        FROM Review r
        WHERE r.product.id = :productId
    """)
    Double calculateAverageRating(@Param("productId") Long productId);
}
