package com.paystream.neftservice.exception;

public class NeftTransactionNotFoundException extends RuntimeException {
    public NeftTransactionNotFoundException(String referenceNumber) {
        super("NEFT transaction not found: " + referenceNumber);
    }
}
