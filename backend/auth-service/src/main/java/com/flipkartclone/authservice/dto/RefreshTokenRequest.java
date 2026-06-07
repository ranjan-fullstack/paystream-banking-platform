package com.flipkartclone.authservice.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}
