package com.paystream.frauddetectionservice.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class FraudAnalysisRequest {

    @JsonProperty("transaction_reference")
    private String transactionReference;

    @JsonProperty("account_number")
    private String accountNumber;

    private BigDecimal amount;

    @JsonProperty("payment_mode")
    private String paymentMode;

    @JsonProperty("beneficiary_account")
    private String beneficiaryAccount;

    @JsonProperty("transaction_time")
    private String transactionTime;

    @JsonProperty("recent_transactions")
    @Builder.Default
    private List<Object> recentTransactions = List.of();

    @JsonProperty("customer_risk_rating")
    private String customerRiskRating;
}
