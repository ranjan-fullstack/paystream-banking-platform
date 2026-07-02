package com.paystream.frauddetectionservice.exception;

public class FraudRuleNotFoundException extends RuntimeException {
    public FraudRuleNotFoundException(String id) {
        super("Fraud rule not found: " + id);
    }
}
