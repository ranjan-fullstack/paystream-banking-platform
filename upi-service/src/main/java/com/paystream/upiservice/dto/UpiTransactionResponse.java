package com.paystream.upiservice.dto;

import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.enums.UpiTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class UpiTransactionResponse {
    private String upiTransactionId;
    private UpiTransactionType transactionType;
    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private UpiTransactionStatus status;
    private String npciTransactionId;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private String failureReason;
}
