package com.paystream.frauddetectionservice.service;

import com.paystream.frauddetectionservice.entity.TransactionLog;

import java.util.Optional;

public interface FraudRuleEngine {
    /**
     * Evaluates all active rules against the given transaction. Returns the
     * first triggered rule's name + risk score + action, or empty if clean.
     */
    Optional<RuleEvaluationResult> evaluate(TransactionLog transaction);

    record RuleEvaluationResult(String ruleTriggered, int riskScore, String action) {
    }
}
