package com.paystream.frauddetectionservice.controller;

import com.paystream.frauddetectionservice.dto.*;
import com.paystream.frauddetectionservice.service.FraudAlertService;
import com.paystream.frauddetectionservice.service.FraudRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
public class FraudController {

    private static final Set<String> ANALYST_ROLES = Set.of("FRAUD_ANALYST", "ADMIN");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

    private final FraudAlertService fraudAlertService;
    private final FraudRuleService fraudRuleService;

    @GetMapping("/alerts")
    public ResponseEntity<List<FraudAlertResponse>> getAlerts(@RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ANALYST_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(fraudAlertService.getAll());
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<FraudAlertResponse> getAlert(@PathVariable UUID id,
                                                        @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ANALYST_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(fraudAlertService.getById(id));
    }

    @PutMapping("/alerts/{id}/review")
    public ResponseEntity<FraudAlertResponse> reviewAlert(@PathVariable UUID id,
                                                            @Valid @RequestBody AlertReviewRequest request,
                                                            @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ANALYST_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(fraudAlertService.review(id, request));
    }

    @GetMapping("/rules")
    public ResponseEntity<List<FraudRuleResponse>> getRules(@RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(fraudRuleService.getAll());
    }

    @PutMapping("/rules/{id}")
    public ResponseEntity<FraudRuleResponse> updateRule(@PathVariable UUID id,
                                                          @Valid @RequestBody FraudRuleUpdateRequest request,
                                                          @RequestHeader(value = "X-USER-ROLE", required = false) String role) {
        if (role == null || !ADMIN_ROLES.contains(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(fraudRuleService.update(id, request));
    }
}
