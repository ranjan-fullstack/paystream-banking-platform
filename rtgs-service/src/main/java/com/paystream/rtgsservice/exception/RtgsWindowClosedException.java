package com.paystream.rtgsservice.exception;

public class RtgsWindowClosedException extends RuntimeException {
    public RtgsWindowClosedException() {
        super("RTGS transfers are only available Mon-Fri 7:00 AM-6:00 PM and Sat 7:00 AM-1:00 PM");
    }
}
