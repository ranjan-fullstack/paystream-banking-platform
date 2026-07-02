package com.paystream.impsservice.dto;

import com.paystream.impsservice.enums.TransferMode;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ImpsTransferRequest {

    @NotBlank
    private String customerId;

    @NotNull
    private TransferMode transferMode;

    @NotBlank
    private String senderAccountNumber;

    private String senderMobile;

    // required when transferMode == ACCOUNT_IFSC
    private String beneficiaryAccountNumber;
    private String beneficiaryIfsc;

    // required when transferMode == MOBILE_MMID
    private String beneficiaryMobile;
    private String beneficiaryMmid;

    @NotBlank
    private String beneficiaryName;

    @NotNull
    @DecimalMin(value = "1.00", message = "IMPS amount must be at least Rs. 1")
    @DecimalMax(value = "500000.00", message = "IMPS amount cannot exceed Rs. 5,00,000 per transaction")
    private BigDecimal amount;

    private String remarks;
}
