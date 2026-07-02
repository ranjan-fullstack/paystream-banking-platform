package com.paystream.rtgsservice.exception;

public class RtgsTransactionNotFoundException extends RuntimeException {
    public RtgsTransactionNotFoundException(String referenceNumber) {
        super("RTGS transaction not found: " + referenceNumber);
    }
}
