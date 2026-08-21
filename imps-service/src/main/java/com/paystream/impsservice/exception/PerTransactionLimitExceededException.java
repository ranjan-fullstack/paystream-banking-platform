package com.paystream.impsservice.exception;

public class PerTransactionLimitExceededException extends RuntimeException {
    public PerTransactionLimitExceededException(String message) {
        super(message);
    }
}
