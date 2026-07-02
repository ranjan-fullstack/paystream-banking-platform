package com.paystream.upiservice.exception;

public class InvalidPinException extends RuntimeException {
    public InvalidPinException() {
        super("Invalid UPI PIN");
    }
}
