package com.paystream.frauddetectionservice.dto;

import com.paystream.frauddetectionservice.enums.RuleAction;
import com.paystream.frauddetectionservice.enums.RuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class FraudRuleResponse {
    private UUID id;
    private String ruleName;
    private RuleType ruleType;
    private BigDecimal threshold;
    private RuleAction action;
    private boolean active;
}
