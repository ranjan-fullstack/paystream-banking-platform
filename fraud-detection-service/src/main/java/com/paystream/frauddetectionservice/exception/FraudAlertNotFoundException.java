package com.paystream.frauddetectionservice.exception;

public class FraudAlertNotFoundException extends RuntimeException {
    public FraudAlertNotFoundException(String id) {
        super("Fraud alert not found: " + id);
    }
}
