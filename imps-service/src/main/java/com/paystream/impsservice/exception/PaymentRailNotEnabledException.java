package com.paystream.impsservice.exception;

public class PaymentRailNotEnabledException extends RuntimeException {
    public PaymentRailNotEnabledException(String message) {
        super(message);
    }
}
