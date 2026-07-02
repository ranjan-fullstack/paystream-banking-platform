package com.paystream.neftservice.dto;

import com.paystream.neftservice.enums.NeftStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class NeftTransactionResponse {
    private String neftReferenceNumber;
    private String senderAccountNumber;
    private String beneficiaryAccountNumber;
    private String beneficiaryName;
    private BigDecimal amount;
    private NeftStatus status;
    private String batchId;
    private LocalDateTime initiatedAt;
    private LocalDateTime completedAt;
    private String failureReason;
}
