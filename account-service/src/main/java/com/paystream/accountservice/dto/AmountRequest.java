package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.PaymentMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class AmountRequest {

    @NotNull
    @Positive
    private BigDecimal amount;

    @NotBlank
    private String referenceNumber;

    private PaymentMode paymentMode;
}
