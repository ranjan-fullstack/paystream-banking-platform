package com.flipkartclone.common.event;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCreatedEvent {

    private Long productId;
    private String title;
    private String brand;
    private Double price;
    private Long categoryId;
}