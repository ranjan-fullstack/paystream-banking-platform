package com.paystream.impsservice.exception;

public class ImpsTransactionNotFoundException extends RuntimeException {
    public ImpsTransactionNotFoundException(String referenceNumber) {
        super("IMPS transaction not found: " + referenceNumber);
    }
}
