package com.paystream.rtgsservice.exception;

public class PerTransactionLimitExceededException extends RuntimeException {
    public PerTransactionLimitExceededException(String message) {
        super(message);
    }
}
