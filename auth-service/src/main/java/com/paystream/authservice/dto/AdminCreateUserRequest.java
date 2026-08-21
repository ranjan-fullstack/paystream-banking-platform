package com.paystream.authservice.dto;

import com.paystream.authservice.entity.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminCreateUserRequest {
    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "password is required")
    private String password;

    @NotNull(message = "role is required")
    private Role role; // CUSTOMER / ADMIN / TELLER / COMPLIANCE_OFFICER / FRAUD_ANALYST
}
