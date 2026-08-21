package com.paystream.upiservice.exception;

public class PerTransactionLimitExceededException extends RuntimeException {
    public PerTransactionLimitExceededException(String message) {
        super(message);
    }
}
