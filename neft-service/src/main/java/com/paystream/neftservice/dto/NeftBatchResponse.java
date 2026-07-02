package com.paystream.neftservice.dto;

import com.paystream.neftservice.enums.NeftBatchStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class NeftBatchResponse {
    private String batchNumber;
    private LocalDateTime scheduledAt;
    private LocalDateTime processedAt;
    private Integer totalTransactions;
    private BigDecimal totalAmount;
    private Integer successCount;
    private Integer failureCount;
    private NeftBatchStatus status;
}
