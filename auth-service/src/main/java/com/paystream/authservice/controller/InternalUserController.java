package com.paystream.authservice.controller;

import com.paystream.authservice.dto.UserBranchInfoResponse;
import com.paystream.authservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service lookups only — reachable via Eureka/Feign directly between
 * services, never routed through api-gateway (no /internal/auth/** predicate
 * exists there), matching account-service's existing /internal/accounts/** convention.
 */
@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class InternalUserController {

    private final AuthService authService;

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserBranchInfoResponse> getUserBranchInfo(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(authService.getUserBranchInfo(userId));
    }
}
