package com.flipkartclone.productservice.dto;

import lombok.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
@NoArgsConstructor   // ✅ REQUIRED
@AllArgsConstructor
@Builder
public class ProductResponse {

    private Long id;
    private String title;
    private String description;
    private Double price;
    private Double discount;
    private String brand;
    private Integer stock;
    private String imageUrl;
    private Double rating;
    private String categoryName;
    private List<ReviewResponse> reviews;
    private Double averageRating;
    private boolean inStock;



}
