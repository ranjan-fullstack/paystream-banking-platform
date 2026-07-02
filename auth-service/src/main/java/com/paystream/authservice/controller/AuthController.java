package com.paystream.authservice.controller;

import com.paystream.authservice.dto.AuthResponse;
import com.paystream.authservice.dto.LoginRequest;
import com.paystream.authservice.dto.RefreshTokenRequest;
import com.paystream.authservice.dto.RegisterRequest;
import com.paystream.authservice.security.JwtService;
import com.paystream.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

    private final AuthService authService;
    private final JwtService jwtService;

    @PostMapping("/auth/v1/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("User registered successfully");
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
