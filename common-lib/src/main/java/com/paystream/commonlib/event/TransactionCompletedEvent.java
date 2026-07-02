package com.paystream.commonlib.event;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionCompletedEvent {

    private String transactionId;
    private String paymentMode; // NEFT, RTGS, IMPS, UPI
    private String paymentReferenceNumber;
    private String debitAccountNumber;
    private String creditAccountNumber;
    private BigDecimal amount;
    private String status;
    private Instant completedAt;
}
