package com.paystream.impsservice.exception;

public class MmidNotFoundException extends RuntimeException {
    public MmidNotFoundException(String mobileNumber) {
        super("No MMID registered for mobile number: " + mobileNumber);
    }
}
