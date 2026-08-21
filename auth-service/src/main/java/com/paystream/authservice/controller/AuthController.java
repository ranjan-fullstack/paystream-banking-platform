package com.paystream.authservice.controller;

import com.paystream.authservice.dto.AdminCreateUserRequest;
import com.paystream.authservice.dto.AuthResponse;
import com.paystream.authservice.dto.BranchDetailResponse;
import com.paystream.authservice.dto.BranchResponse;
import com.paystream.authservice.dto.ChangePasswordRequest;
import com.paystream.authservice.dto.CreateBranchManagerRequest;
import com.paystream.authservice.dto.CreateBranchManagerResponse;
import com.paystream.authservice.dto.CreateBranchRequest;
import com.paystream.authservice.dto.CreateCustomerByBranchRequest;
import com.paystream.authservice.dto.CreateCustomerByBranchResponse;
import com.paystream.authservice.dto.LoginRequest;
import com.paystream.authservice.dto.RefreshTokenRequest;
import com.paystream.authservice.dto.RegisterRequest;
import com.paystream.authservice.exception.AccessDeniedException;
import com.paystream.authservice.security.JwtService;
import com.paystream.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Auth APIs", description = "Auth Management APIs")
@RequiredArgsConstructor
public class AuthController {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_BRANCH_MANAGER = "BRANCH_MANAGER";

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/auth/v1/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/auth/v1/admin/users")
    public ResponseEntity<String> createUserByAdmin(
            @Valid @RequestBody AdminCreateUserRequest request,
            @RequestHeader(value = "X-USER-ROLE", required = false) String callerRole) {
        if (!ROLE_ADMIN.equals(callerRole)) {
            throw new AccessDeniedException("Only ADMIN may create users with an elevated role");
        }
        authService.registerByAdmin(request);
        return ResponseEntity.ok("User created successfully");
    }

    @PostMapping("/auth/v1/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/auth/v1/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/auth/v1/logout")
    public ResponseEntity<String> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok("Logged out successfully");
    }

    @PostMapping("/auth/v1/admin/create-branch")
    public ResponseEntity<BranchResponse> createBranch(
            @Valid @RequestBody CreateBranchRequest request,
            @RequestHeader(value = "X-USER-ROLE", required = false) String callerRole) {
        if (!ROLE_ADMIN.equals(callerRole)) {
            throw new AccessDeniedException("Only ADMIN may create branches");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createBranch(request));
    }

    @PostMapping("/auth/v1/admin/create-branch-manager")
    public ResponseEntity<CreateBranchManagerResponse> createBranchManager(
            @Valid @RequestBody CreateBranchManagerRequest request,
            @RequestHeader(value = "X-USER-ROLE", required = false) String callerRole) {
        if (!ROLE_ADMIN.equals(callerRole)) {
            throw new AccessDeniedException("Only ADMIN may create branch managers");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.createBranchManager(request));
    }

    @PostMapping("/auth/v1/branch/create-customer")
    public ResponseEntity<CreateCustomerByBranchResponse> createCustomerByBranch(
            @Valid @RequestBody CreateCustomerByBranchRequest request,
            @RequestHeader(value = "X-USER-ROLE", required = false) String callerRole,
            @RequestHeader(value = "X-USER-ID", required = false) Long callerUserId) {
        if (!ROLE_BRANCH_MANAGER.equals(callerRole)) {
            throw new AccessDeniedException("Only a branch manager may create a customer");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.createCustomerByBranch(callerUserId, request));
    }

    @PostMapping("/auth/v1/change-password")
    public ResponseEntity<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "X-USER-ID", required = false) Long callerUserId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        authService.changePassword(callerUserId, request, authHeader);
        return ResponseEntity.ok("Password changed successfully");
    }

    @GetMapping("/auth/v1/admin/branches")
    public ResponseEntity<List<BranchResponse>> getBranches(
            @RequestHeader(value = "X-USER-ROLE", required = false) String callerRole) {
        if (!ROLE_ADMIN.equals(callerRole)) {
            throw new AccessDeniedException("Only ADMIN may view branches");
        }
        return ResponseEntity.ok(authService.getBranches());
    }

    @GetMapping("/auth/v1/admin/branches/{branchCode}")
    public ResponseEntity<BranchDetailResponse> getBranchDetail(
            @PathVariable("branchCode") String branchCode,
            @RequestHeader(value = "X-USER-ROLE", required = false) String callerRole) {
        if (!ROLE_ADMIN.equals(callerRole)) {
            throw new AccessDeniedException("Only ADMIN may view branch details");
        }
        return ResponseEntity.ok(authService.getBranchDetail(branchCode));
    }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        RSAPublicKey publicKey = jwtService.getPublicKey();

        byte[] nBytes = toUnsignedBytes(publicKey.getModulus());
        byte[] eBytes = toUnsignedBytes(publicKey.getPublicExponent());
        String n = Base64.getUrlEncoder().withoutPadding().encodeToString(nBytes);
        String e = Base64.getUrlEncoder().withoutPadding().encodeToString(eBytes);

        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("kid", "jwt-signing-key");
        key.put("n", n);
        key.put("e", e);

        return ResponseEntity.ok(Map.of("keys", List.of(key)));
    }

    private byte[] toUnsignedBytes(BigInteger bigInt) {
        byte[] bytes = bigInt.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }
}
