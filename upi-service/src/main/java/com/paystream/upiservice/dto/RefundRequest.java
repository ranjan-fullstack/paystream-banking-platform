package com.paystream.upiservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RefundRequest {

    @NotBlank
    private String originalUpiTransactionId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private String reason;
}
