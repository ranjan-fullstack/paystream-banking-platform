package com.paystream.accountservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_payment_configs")
@Getter
@Setter
public class AccountPaymentConfig {

    // Standard-tier default: NEFT and IMPS both default to this limit before
    // any enterprise/premium tier override is applied.
    private static final BigDecimal DEFAULT_STANDARD_LIMIT = new BigDecimal("500000.00");

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private UUID accountId;

    @Column(nullable = false)
    private boolean neftEnabled = false;

    @Column(nullable = false)
    private boolean rtgsEnabled = false;

    @Column(nullable = false)
    private boolean impsEnabled = false;

    @Column(nullable = false)
    private boolean upiEnabled = false;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal neftDailyLimit = DEFAULT_STANDARD_LIMIT;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal rtgsDailyLimit = new BigDecimal("10000000.00");

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal impsDailyLimit = DEFAULT_STANDARD_LIMIT;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal upiDailyLimit = new BigDecimal("100000.00");

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal neftPerTransactionLimit = new BigDecimal("1000000.00");

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal rtgsPerTransactionLimit = new BigDecimal("10000000.00");

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal impsPerTransactionLimit = DEFAULT_STANDARD_LIMIT;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal upiPerTransactionLimit = new BigDecimal("100000.00");

    private String enabledBy;

    private LocalDateTime enabledAt;

    private String lastUpdatedBy;

    private LocalDateTime lastUpdatedAt;
}
