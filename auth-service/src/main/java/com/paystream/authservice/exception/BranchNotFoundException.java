package com.paystream.authservice.exception;

public class BranchNotFoundException extends RuntimeException {
    public BranchNotFoundException(String branchCode) {
        super("Branch not found: " + branchCode);
    }
}
