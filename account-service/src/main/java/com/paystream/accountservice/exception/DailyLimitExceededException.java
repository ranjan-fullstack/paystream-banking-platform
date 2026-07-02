package com.paystream.accountservice.exception;

import java.math.BigDecimal;

public class DailyLimitExceededException extends RuntimeException {

    public DailyLimitExceededException(String paymentMode, BigDecimal dailyLimit) {
        super("Daily " + paymentMode + " transfer limit of Rs " + dailyLimit + " has been reached for today");
    }
}
