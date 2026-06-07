package com.flipkartclone.productservice.exception;

public class OptimisticLockingFailureException extends RuntimeException {
    public OptimisticLockingFailureException(String msg) {
        super(msg);
    }
}
