package com.paystream.rtgsservice.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
public class AmountRequest {
    private BigDecimal amount;
    private String referenceNumber;
    private String paymentMode;
}
