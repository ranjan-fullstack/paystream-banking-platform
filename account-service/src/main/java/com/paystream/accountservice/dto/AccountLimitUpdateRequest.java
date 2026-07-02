package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.PaymentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AccountLimitUpdateRequest {

    @NotNull
    private PaymentMode transactionType;

    @NotNull
    @Positive
    private BigDecimal dailyLimit;

    @NotNull
    @Positive
    private BigDecimal perTransactionLimit;
}
