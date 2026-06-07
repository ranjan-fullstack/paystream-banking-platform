package com.flipkartclone.productservice.model;

import jakarta.persistence.*;


import lombok.*;

import java.util.List;

@Entity
@Table(name = "category")   // <-- FIX THIS
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @OneToMany(mappedBy = "category")
    private List<Product> products;
}

