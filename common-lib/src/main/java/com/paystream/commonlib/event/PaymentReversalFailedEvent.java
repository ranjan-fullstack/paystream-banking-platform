package com.paystream.commonlib.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentReversalFailedEvent {

    private String paymentReferenceNumber;
    private String paymentMode;
    private String senderAccountNumber;
    private BigDecimal amount;
    private String failureReason;
    private Instant failedAt;
}
