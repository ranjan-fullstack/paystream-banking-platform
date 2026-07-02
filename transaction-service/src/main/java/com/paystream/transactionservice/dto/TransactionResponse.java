package com.paystream.transactionservice.dto;

import com.paystream.transactionservice.enums.PaymentMode;
import com.paystream.transactionservice.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class TransactionResponse {
    private String transactionId;
    private PaymentMode paymentMode;
    private String paymentReferenceNumber;
    private String debitAccountNumber;
    private String creditAccountNumber;
    private BigDecimal amount;
    private String currency;
    private TransactionStatus status;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String description;
}
