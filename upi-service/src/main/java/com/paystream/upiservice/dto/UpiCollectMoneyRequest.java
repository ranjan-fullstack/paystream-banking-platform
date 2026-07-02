package com.paystream.upiservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpiCollectMoneyRequest {

    @NotBlank
    private String requestedByVpa; // payee, who will receive the money

    @NotBlank
    private String requestedFromVpa; // payer, who will be asked to pay

    @NotNull
    @DecimalMin(value = "1.00", message = "UPI amount must be at least Rs. 1")
    @DecimalMax(value = "100000.00", message = "UPI amount cannot exceed Rs. 1,00,000 per transaction")
    private BigDecimal amount;

    private String remarks;
}
