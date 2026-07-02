package com.paystream.upiservice.exception;

public class UpiTransactionNotFoundException extends RuntimeException {
    public UpiTransactionNotFoundException(String transactionId) {
        super("UPI transaction not found: " + transactionId);
    }
}
