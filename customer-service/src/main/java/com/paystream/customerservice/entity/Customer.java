package com.paystream.customerservice.entity;

import com.paystream.customerservice.config.PiiEncryptionConverter;
import com.paystream.customerservice.enums.KycStatus;
import com.paystream.customerservice.enums.RiskRating;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@Setter
public class Customer {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String customerId; // CIF number, e.g. CIF001234

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(unique = true, nullable = false)
    private String mobile;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(nullable = false)
    private String panNumber;

    @Convert(converter = PiiEncryptionConverter.class)
    @Column(nullable = false)
    private String aadhaarNumber;

    @Column(nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RiskRating riskRating = RiskRating.LOW;

    @Column(nullable = false)
    private Long userId; // FK reference to auth-service User.id

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
