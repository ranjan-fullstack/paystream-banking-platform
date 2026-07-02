package com.paystream.upiservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "virtual_payment_addresses")
@Getter
@Setter
public class VirtualPaymentAddress {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String vpa; // handle@paystream

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private boolean isDefault = false;

    @Column(nullable = false)
    private boolean active = true;

    private String upiPin; // BCrypt hashed

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
