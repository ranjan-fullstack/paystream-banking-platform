package com.paystream.authservice.exception;

public class DuplicateBranchException extends RuntimeException {
    public DuplicateBranchException(String branchCode) {
        super("Branch code already exists: " + branchCode);
    }
}
