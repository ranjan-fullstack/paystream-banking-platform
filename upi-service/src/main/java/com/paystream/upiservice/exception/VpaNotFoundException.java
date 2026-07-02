package com.paystream.upiservice.exception;

public class VpaNotFoundException extends RuntimeException {
    public VpaNotFoundException(String vpa) {
        super("VPA not found: " + vpa);
    }
}
