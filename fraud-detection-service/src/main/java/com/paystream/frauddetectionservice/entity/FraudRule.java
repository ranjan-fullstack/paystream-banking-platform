package com.paystream.frauddetectionservice.entity;

import com.paystream.frauddetectionservice.enums.RuleAction;
import com.paystream.frauddetectionservice.enums.RuleType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "fraud_rules")
@Getter
@Setter
public class FraudRule {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleType ruleType;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleAction action;

    @Column(nullable = false)
    private boolean active = true;
}
