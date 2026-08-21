package com.paystream.neftservice.exception;

public class PerTransactionLimitExceededException extends RuntimeException {
    public PerTransactionLimitExceededException(String message) {
        super(message);
    }
}
