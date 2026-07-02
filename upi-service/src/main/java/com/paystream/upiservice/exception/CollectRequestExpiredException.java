package com.paystream.upiservice.exception;

public class CollectRequestExpiredException extends RuntimeException {
    public CollectRequestExpiredException(String transactionId) {
        super("Collect request has expired: " + transactionId);
    }
}
