package com.flipkartclone.productservice.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_variants")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String variantName;       // e.g. Color, RAM, Size
    private String variantValue;      // e.g. Black, 8GB, Large
    private Double additionalPrice;   // e.g. +1000 for 8GB RAM

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;
}
