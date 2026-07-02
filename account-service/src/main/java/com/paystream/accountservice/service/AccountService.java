package com.paystream.accountservice.service;

import com.paystream.accountservice.dto.*;

import java.util.List;
import java.util.UUID;

public interface AccountService {
    AccountResponse openAccount(OpenAccountRequest request);
    AccountResponse getById(UUID accountId);
    AccountResponse getByAccountNumber(String accountNumber);
    List<AccountResponse> getByCustomerId(String customerId);
    AccountResponse updateStatus(UUID accountId, AccountStatusUpdateRequest request);
    BalanceResponse getBalance(UUID accountId);
    List<AccountLimitResponse> getLimits(UUID accountId);
    AccountLimitResponse updateLimit(UUID accountId, AccountLimitUpdateRequest request);

    AccountValidationResponse validate(String accountNumber);
    void debit(String accountNumber, AmountRequest request);
    void credit(String accountNumber, AmountRequest request);
    void hold(String accountNumber, AmountRequest request);
    void release(String accountNumber, AmountRequest request);
    void reverseDebit(String accountNumber, AmountRequest request);
}
