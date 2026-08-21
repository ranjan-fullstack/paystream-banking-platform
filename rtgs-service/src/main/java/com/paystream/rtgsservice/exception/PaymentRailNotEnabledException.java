package com.paystream.rtgsservice.exception;

public class PaymentRailNotEnabledException extends RuntimeException {
    public PaymentRailNotEnabledException(String message) {
        super(message);
    }
}
