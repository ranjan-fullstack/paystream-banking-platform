package com.paystream.accountservice.entity;

import com.paystream.accountservice.enums.PaymentMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_limits", uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "transaction_type"}))
@Getter
@Setter
public class AccountLimit {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private BankAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private PaymentMode transactionType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dailyLimit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal perTransactionLimit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal usedTodayAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private LocalDateTime resetAt;
}
