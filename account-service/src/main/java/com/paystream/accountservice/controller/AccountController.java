package com.paystream.accountservice.controller;

import com.paystream.accountservice.dto.*;
import com.paystream.accountservice.exception.AccessDeniedException;
import com.paystream.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@Valid @RequestBody OpenAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.openAccount(request));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getById(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-USER-ID", required = false) String userId,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        AccountResponse account = accountService.getById(accountId);
        if (ROLE_CUSTOMER.equals(role)) {
            verifyOwner(userId, account.getUserId());
        }
        return ResponseEntity.ok(account);
    }

    @GetMapping("/number/{accountNumber}")
    public ResponseEntity<AccountResponse> getByAccountNumber(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.getByAccountNumber(accountNumber));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountResponse>> getByCustomerId(
            @PathVariable String customerId,
            @RequestHeader(value = "X-USER-ID", required = false) String userId,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        List<AccountResponse> accounts = accountService.getByCustomerId(customerId);
        if (ROLE_CUSTOMER.equals(role) && !accounts.isEmpty()) {
            verifyOwner(userId, accounts.get(0).getUserId());
        }
        return ResponseEntity.ok(accounts);
    }

    @PutMapping("/{accountId}/status")
    public ResponseEntity<AccountResponse> updateStatus(@PathVariable UUID accountId,
                                                          @Valid @RequestBody AccountStatusUpdateRequest request) {
        return ResponseEntity.ok(accountService.updateStatus(accountId, request));
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-USER-ID", required = false) String userId,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (ROLE_CUSTOMER.equals(role)) {
            checkOwnership(accountId, userId);
        }
        return ResponseEntity.ok(accountService.getBalance(accountId));
    }

    @GetMapping("/{accountId}/limits")
    public ResponseEntity<List<AccountLimitResponse>> getLimits(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-USER-ID", required = false) String userId,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (ROLE_CUSTOMER.equals(role)) {
            checkOwnership(accountId, userId);
        }
        return ResponseEntity.ok(accountService.getLimits(accountId));
    }

    @PutMapping("/{accountId}/limits")
    public ResponseEntity<AccountLimitResponse> updateLimit(@PathVariable UUID accountId,
                                                              @Valid @RequestBody AccountLimitUpdateRequest request) {
        return ResponseEntity.ok(accountService.updateLimit(accountId, request));
    }

    private void checkOwnership(UUID accountId, String requestingUserId) {
        AccountResponse account = accountService.getById(accountId);
        verifyOwner(requestingUserId, account.getUserId());
    }

    private void verifyOwner(String requestingUserId, Long accountOwnerId) {
        if (accountOwnerId == null || !accountOwnerId.toString().equals(requestingUserId)) {
            throw new AccessDeniedException("Access denied: account does not belong to the requesting user");
        }
    }
}
