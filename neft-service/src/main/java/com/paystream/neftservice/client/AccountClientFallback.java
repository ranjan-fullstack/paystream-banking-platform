package com.paystream.neftservice.client;

import com.paystream.neftservice.client.dto.AccountValidationResponse;
import com.paystream.neftservice.client.dto.AmountRequest;
import com.paystream.neftservice.client.dto.PaymentRailConfigResponse;
import com.paystream.neftservice.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AccountClientFallback implements AccountClient {

    @Override
    public AccountValidationResponse validate(String accountNumber) {
        log.warn("Circuit breaker triggered for account-service validate({})", accountNumber);
        AccountValidationResponse fallback = new AccountValidationResponse();
        fallback.setValid(false);
        fallback.setReason("Account service unavailable");
        return fallback;
    }

    @Override
    public void debit(String accountNumber, AmountRequest request) {
        log.error("Circuit breaker triggered for account-service debit({})", accountNumber);
        throw new ServiceUnavailableException("Account service unavailable — cannot process debit for " + accountNumber);
    }

    @Override
    public void credit(String accountNumber, AmountRequest request) {
        log.error("Circuit breaker triggered for account-service credit({})", accountNumber);
        throw new ServiceUnavailableException("Account service unavailable — cannot process credit for " + accountNumber);
    }

    @Override
    public void reverseDebit(String accountNumber, AmountRequest request) {
        log.error("Circuit breaker triggered for account-service reverseDebit({})", accountNumber);
        throw new ServiceUnavailableException("Account service unavailable — cannot process reversal for " + accountNumber);
    }

    @Override
    public PaymentRailConfigResponse getPaymentConfig(String accountNumber, String paymentMode) {
        log.error("Circuit breaker triggered for account-service getPaymentConfig({}, {})", accountNumber, paymentMode);
        throw new ServiceUnavailableException("Account service unavailable — cannot verify " + paymentMode + " rail status for " + accountNumber);
    }
}