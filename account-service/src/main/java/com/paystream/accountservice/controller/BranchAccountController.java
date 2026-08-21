package com.paystream.accountservice.controller;

import com.paystream.accountservice.client.AuthClient;
import com.paystream.accountservice.client.dto.UserBranchInfoResponse;
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
@RequestMapping("/api/v1/branch/accounts")
@RequiredArgsConstructor
public class BranchAccountController {

    private static final String ROLE_BRANCH_MANAGER = "BRANCH_MANAGER";
    private static final String ROLE_CUSTOMER = "CUSTOMER";

    private final AccountService accountService;
    private final AuthClient authClient;

    @PostMapping("/open")
    public ResponseEntity<AccountResponse> openAccount(
            @Valid @RequestBody OpenAccountByBranchRequest request,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        requireBranchManager(role);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.openAccountByBranch(request));
    }

    @PutMapping("/{accountId}/payment-config")
    public ResponseEntity<PaymentConfigResponse> updatePaymentConfig(
            @PathVariable UUID accountId,
            @Valid @RequestBody PaymentConfigUpdateRequest request,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        requireBranchManager(role);
        return ResponseEntity.ok(accountService.updatePaymentConfig(accountId, request));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccountsForMyBranch(
            @RequestHeader(value = "X-USER-ROLE", required = false) String role,
            @RequestHeader(value = "X-USER-ID", required = false) Long userId) {
        requireBranchManager(role);
        UserBranchInfoResponse caller = authClient.getUserBranchInfo(userId);
        return ResponseEntity.ok(accountService.getAccountsByBranch(caller.getBranchCode()));
    }

    @GetMapping("/{accountId}/payment-config")
    public ResponseEntity<PaymentConfigResponse> getPaymentConfig(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role,
            @RequestHeader(value = "X-USER-ID", required = false) String userId) {
        if (ROLE_CUSTOMER.equals(role)) {
            AccountResponse account = accountService.getById(accountId);
            if (account.getUserId() == null || !account.getUserId().toString().equals(userId)) {
                throw new AccessDeniedException("Access denied: account does not belong to the requesting user");
            }
        } else if (!ROLE_BRANCH_MANAGER.equals(role)) {
            throw new AccessDeniedException("Only a branch manager or the owning customer may view payment config");
        }
        return ResponseEntity.ok(accountService.getPaymentConfig(accountId));
    }

    @PutMapping("/{accountId}/freeze")
    public ResponseEntity<String> freeze(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        requireBranchManager(role);
        accountService.freezeAccount(accountId);
        return ResponseEntity.ok("Account frozen successfully");
    }

    @PutMapping("/{accountId}/unfreeze")
    public ResponseEntity<String> unfreeze(
            @PathVariable UUID accountId,
            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        requireBranchManager(role);
        accountService.unfreezeAccount(accountId);
        return ResponseEntity.ok("Account unfrozen successfully");
    }

    private void requireBranchManager(String role) {
        if (!ROLE_BRANCH_MANAGER.equals(role)) {
            throw new AccessDeniedException("Only a branch manager may perform this action");
        }
    }
}
