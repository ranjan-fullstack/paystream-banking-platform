package com.paystream.neftservice.entity;

import com.paystream.neftservice.enums.NeftStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "neft_transactions")
@Getter
@Setter
public class NeftTransaction {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String neftReferenceNumber; // NEFT + YYYYMMDD + sequence

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String senderAccountNumber;

    @Column(nullable = false)
    private String senderIfsc;

    @Column(nullable = false)
    private String beneficiaryAccountNumber;

    @Column(nullable = false)
    private String beneficiaryIfsc;

    @Column(nullable = false)
    private String beneficiaryName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NeftStatus status = NeftStatus.INITIATED;

    private String batchId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    private LocalDateTime batchProcessedAt;

    private LocalDateTime completedAt;

    private String failureReason;

    @Column(nullable = false)
    private Integer retryCount = 0;

    @PrePersist
    protected void onCreate() {
        initiatedAt = LocalDateTime.now();
    }
}
