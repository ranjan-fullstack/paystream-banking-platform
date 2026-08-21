package com.paystream.accountservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class PaymentConfigResponse {
    private UUID accountId;

    private boolean neftEnabled;
    private BigDecimal neftDailyLimit;
    private BigDecimal neftPerTransactionLimit;

    private boolean rtgsEnabled;
    private BigDecimal rtgsDailyLimit;
    private BigDecimal rtgsPerTransactionLimit;

    private boolean impsEnabled;
    private BigDecimal impsDailyLimit;
    private BigDecimal impsPerTransactionLimit;

    private boolean upiEnabled;
    private BigDecimal upiDailyLimit;
    private BigDecimal upiPerTransactionLimit;

    private String enabledBy;
    private LocalDateTime enabledAt;
    private String lastUpdatedBy;
    private LocalDateTime lastUpdatedAt;
}
