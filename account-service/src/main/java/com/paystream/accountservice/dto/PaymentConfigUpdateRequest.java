package com.paystream.accountservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentConfigUpdateRequest {

    @NotNull(message = "neftEnabled is required")
    private Boolean neftEnabled;
    private BigDecimal neftDailyLimit;
    private BigDecimal neftPerTransactionLimit;

    @NotNull(message = "rtgsEnabled is required")
    private Boolean rtgsEnabled;
    private BigDecimal rtgsDailyLimit;
    private BigDecimal rtgsPerTransactionLimit;

    @NotNull(message = "impsEnabled is required")
    private Boolean impsEnabled;
    private BigDecimal impsDailyLimit;
    private BigDecimal impsPerTransactionLimit;

    @NotNull(message = "upiEnabled is required")
    private Boolean upiEnabled;
    private BigDecimal upiDailyLimit;
    private BigDecimal upiPerTransactionLimit;

    private String updatedBy;
}
