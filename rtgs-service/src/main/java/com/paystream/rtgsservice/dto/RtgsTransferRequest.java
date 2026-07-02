package com.paystream.rtgsservice.dto;

import com.paystream.rtgsservice.enums.RtgsPurpose;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class RtgsTransferRequest {

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
    @DecimalMin(value = "200000.00", message = "RTGS amount must be at least Rs. 2,00,000")
    private BigDecimal amount;

    @NotNull
    private RtgsPurpose purpose;
}
