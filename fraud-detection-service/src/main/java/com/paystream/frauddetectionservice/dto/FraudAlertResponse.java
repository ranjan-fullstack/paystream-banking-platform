package com.paystream.frauddetectionservice.dto;

import com.paystream.frauddetectionservice.enums.AlertStatus;
import com.paystream.frauddetectionservice.enums.RuleAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class FraudAlertResponse {
    private UUID id;
    private String accountNumber;
    private String transactionReference;
    private String ruleTriggered;
    private Integer riskScore;
    private RuleAction action;
    private AlertStatus status;
    private String reviewRemarks;
    private LocalDateTime createdAt;
}
