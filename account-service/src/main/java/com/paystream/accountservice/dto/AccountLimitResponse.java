package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.PaymentMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AccountLimitResponse {
    private UUID id;
    private PaymentMode transactionType;
    private BigDecimal dailyLimit;
    private BigDecimal perTransactionLimit;
    private BigDecimal usedTodayAmount;
}
