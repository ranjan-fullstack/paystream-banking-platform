package com.paystream.frauddetectionservice.dto;

import com.paystream.frauddetectionservice.enums.RuleAction;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class FraudRuleUpdateRequest {

    @NotNull
    private BigDecimal threshold;

    @NotNull
    private RuleAction action;

    @NotNull
    private Boolean active;
}
