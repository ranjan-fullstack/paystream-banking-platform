package com.paystream.neftservice.exception;

public class NeftWindowClosedException extends RuntimeException {
    public NeftWindowClosedException() {
        super("NEFT transfers are only available Monday-Saturday, 8:00 AM to 7:00 PM");
    }
}
