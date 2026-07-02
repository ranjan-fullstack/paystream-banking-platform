package com.paystream.neftservice.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class NeftTransferRequest {

    @NotBlank
    private String customerId;

    @NotBlank
    private String senderAccountNumber;

    @NotBlank
    private String senderIfsc;

    @NotBlank
    private String beneficiaryAccountNumber;

    @NotBlank
    private String beneficiaryIfsc;

    @NotBlank
    private String beneficiaryName;

    @NotNull
    @DecimalMin(value = "0.01", message = "amount must be greater than zero")
    @DecimalMax(value = "1000000", message = "NEFT amount cannot exceed Rs. 10,00,000 per transaction")
    private BigDecimal amount;

    private String remarks;
}
