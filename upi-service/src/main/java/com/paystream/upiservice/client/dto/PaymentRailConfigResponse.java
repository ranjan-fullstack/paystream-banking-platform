package com.paystream.upiservice.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentRailConfigResponse {
    private boolean enabled;
    private BigDecimal perTransactionLimit;
    private BigDecimal dailyLimit;
    private BigDecimal usedToday;
    private BigDecimal remainingToday;
}
