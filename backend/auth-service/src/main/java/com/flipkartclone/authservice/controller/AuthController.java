package com.flipkartclone.authservice.controller;

import com.flipkartclone.authservice.dto.AuthResponse;
import com.flipkartclone.authservice.dto.LoginRequest;
import com.flipkartclone.authservice.dto.RefreshTokenRequest;
import com.flipkartclone.authservice.dto.RegisterRequest;
import com.flipkartclone.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
//@RequestMapping("/auth")
@RequestMapping("/auth/v1")
@Tag(name = "Auth APIs", description = "Auth Management APIs")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refreshToken(request));
    }
}
