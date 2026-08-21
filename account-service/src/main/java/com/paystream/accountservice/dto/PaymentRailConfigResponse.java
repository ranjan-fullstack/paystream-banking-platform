package com.paystream.accountservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PaymentRailConfigResponse {
    private boolean enabled;
    private BigDecimal perTransactionLimit;
    private BigDecimal dailyLimit;
    private BigDecimal usedToday;
    private BigDecimal remainingToday;
}
