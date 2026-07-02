package com.paystream.upiservice.entity;

import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.enums.UpiTransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "upi_transactions")
@Getter
@Setter
public class UpiTransaction {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String upiTransactionId; // UPI + timestamp

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpiTransactionType transactionType;

    @Column(nullable = false)
    private String senderVpa;

    @Column(nullable = false)
    private String receiverVpa;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UpiTransactionStatus status = UpiTransactionStatus.INITIATED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    private LocalDateTime completedAt;

    private LocalDateTime expiresAt; // collect requests expire in 30 minutes

    private String npciTransactionId;

    private String failureReason;

    @PrePersist
    protected void onCreate() {
        initiatedAt = LocalDateTime.now();
    }
}
