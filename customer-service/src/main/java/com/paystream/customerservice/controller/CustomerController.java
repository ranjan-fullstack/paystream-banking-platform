package com.paystream.customerservice.controller;

import com.paystream.customerservice.dto.*;
import com.paystream.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
public class CustomerController {

    private static final Set<String> KYC_VERIFIER_ROLES = Set.of("TELLER", "ADMIN", "COMPLIANCE_OFFICER");

    private final CustomerService customerService;

    @PostMapping("/register")
    public ResponseEntity<CustomerResponse> register(@Valid @RequestBody CustomerRegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.register(request));
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> get(@PathVariable String customerId) {
        return ResponseEntity.ok(customerService.getByCustomerId(customerId));
    }

    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> update(@PathVariable String customerId,
                                                     @Valid @RequestBody CustomerUpdateRequest request) {
        return ResponseEntity.ok(customerService.update(customerId, request));
    }

    @PostMapping("/{customerId}/kyc")
    public ResponseEntity<KycStatusResponse> submitKyc(@PathVariable String customerId,
                                                        @Valid @RequestBody KycSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(customerService.submitKyc(customerId, request));
    }

    @GetMapping("/{customerId}/kyc")
    public ResponseEntity<KycStatusResponse> getKycStatus(@PathVariable String customerId) {
        return ResponseEntity.ok(customerService.getKycStatus(customerId));
    }

    @PutMapping("/{customerId}/kyc/verify")
    public ResponseEntity<KycStatusResponse> verifyKyc(@PathVariable String customerId,
                                                        @Valid @RequestBody KycVerifyRequest request,
                                                        @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !KYC_VERIFIER_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(customerService.verifyKyc(customerId, request));
    }
}
