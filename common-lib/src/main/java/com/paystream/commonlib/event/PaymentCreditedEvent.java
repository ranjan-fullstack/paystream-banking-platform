package com.paystream.commonlib.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentCreditedEvent {

    private String paymentReferenceNumber;
    private String paymentMode;
    private String senderAccountNumber;
    private String beneficiaryAccountNumber;
    private BigDecimal amount;
    private Instant creditedAt;
}
