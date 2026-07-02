package com.paystream.transactionservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class TransactionSummaryResponse {
    private String accountNumber;
    private long totalTransactions;
    private BigDecimal totalAmount;
    private Map<String, Long> countByMode;
    private Map<String, BigDecimal> totalAmountByMode;
}
