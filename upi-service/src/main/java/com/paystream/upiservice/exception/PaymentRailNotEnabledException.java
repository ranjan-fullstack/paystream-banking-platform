package com.paystream.upiservice.exception;

public class PaymentRailNotEnabledException extends RuntimeException {
    public PaymentRailNotEnabledException(String message) {
        super(message);
    }
}
