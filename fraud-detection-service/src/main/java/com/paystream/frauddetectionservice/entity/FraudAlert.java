package com.paystream.frauddetectionservice.entity;

import com.paystream.frauddetectionservice.enums.AlertStatus;
import com.paystream.frauddetectionservice.enums.RuleAction;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fraud_alerts")
@Getter
@Setter
public class FraudAlert {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private String transactionReference;

    @Column(nullable = false)
    private String ruleTriggered;

    @Column(nullable = false)
    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status = AlertStatus.OPEN;

    private String reviewRemarks;

    @Column(columnDefinition = "TEXT")
    private String aiExplanation;

    private Float aiRiskScore;

    // RULE_ENGINE / AI / RULE_ENGINE + AI
    private String detectionMethod;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
