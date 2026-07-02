package com.paystream.rtgsservice.exception;

public class RtgsAlreadySettledException extends RuntimeException {
    public RtgsAlreadySettledException(String referenceNumber) {
        super("RTGS transaction already settled and cannot be recalled: " + referenceNumber);
    }
}
