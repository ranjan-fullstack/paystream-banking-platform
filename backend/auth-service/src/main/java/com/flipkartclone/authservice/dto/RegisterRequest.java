package com.flipkartclone.authservice.dto;

import com.flipkartclone.authservice.entity.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    private String username;
    private String password;
    private Role role; // ROLE_USER / ROLE_ADMIN
}

