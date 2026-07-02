package com.paystream.accountservice.controller.internal;

import com.paystream.accountservice.dto.AccountValidationResponse;
import com.paystream.accountservice.dto.AmountRequest;
import com.paystream.accountservice.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
public class InternalAccountController {

    private final AccountService accountService;

    @GetMapping("/{accountNumber}/validate")
    public ResponseEntity<AccountValidationResponse> validate(@PathVariable String accountNumber) {
        return ResponseEntity.ok(accountService.validate(accountNumber));
    }

    @PutMapping("/{accountNumber}/debit")
    public ResponseEntity<Void> debit(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        accountService.debit(accountNumber, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{accountNumber}/credit")
    public ResponseEntity<Void> credit(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        accountService.credit(accountNumber, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{accountNumber}/hold")
    public ResponseEntity<Void> hold(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        accountService.hold(accountNumber, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{accountNumber}/release")
    public ResponseEntity<Void> release(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        accountService.release(accountNumber, request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{accountNumber}/reverseDebit")
    public ResponseEntity<Void> reverseDebit(@PathVariable String accountNumber, @Valid @RequestBody AmountRequest request) {
        accountService.reverseDebit(accountNumber, request);
        return ResponseEntity.ok().build();
    }
}
