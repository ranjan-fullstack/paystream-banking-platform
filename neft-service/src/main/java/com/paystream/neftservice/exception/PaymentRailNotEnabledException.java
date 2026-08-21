package com.paystream.neftservice.exception;

public class PaymentRailNotEnabledException extends RuntimeException {
    public PaymentRailNotEnabledException(String message) {
        super(message);
    }
}
