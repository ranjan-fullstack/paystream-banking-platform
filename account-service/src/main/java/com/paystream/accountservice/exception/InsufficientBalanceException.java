package com.paystream.accountservice.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String accountNumber) {
        super("Insufficient available balance on account: " + accountNumber);
    }
}
