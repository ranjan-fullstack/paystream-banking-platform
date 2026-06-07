package com.flipkartclone.productservice.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductCreatedEvent {

    private Long productId;
    private String title;
    private String brand;
    private Double price;
    private Long categoryId;
}
