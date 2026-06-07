package com.flipkartclone.inventoryservice.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "inventory")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Inventory {

    @Id
    private Long productId; // same as product-service product id

    private Integer quantity;
}
