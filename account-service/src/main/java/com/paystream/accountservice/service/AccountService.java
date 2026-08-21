package com.paystream.accountservice.service;

import com.paystream.accountservice.dto.*;
import com.paystream.accountservice.enums.PaymentMode;

import java.util.List;
import java.util.UUID;

public interface AccountService {
    AccountResponse openAccount(OpenAccountRequest request);
    AccountResponse getById(UUID accountId);
    AccountResponse getByAccountNumber(String accountNumber);
    List<AccountResponse> getByCustomerId(String customerId);
    List<AccountResponse> getByUserId(Long userId);
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

    AccountResponse openAccountByBranch(OpenAccountByBranchRequest request);
    PaymentConfigResponse updatePaymentConfig(UUID accountId, PaymentConfigUpdateRequest request);
    List<AccountResponse> getAccountsByBranch(String branchCode);
    PaymentConfigResponse getPaymentConfig(UUID accountId);
    AccountResponse freezeAccount(UUID accountId);
    AccountResponse unfreezeAccount(UUID accountId);

    PaymentRailConfigResponse getPaymentRailConfig(String accountNumber, PaymentMode paymentMode);
}
