package com.paystream.authservice.dto;

import com.paystream.authservice.entity.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {
    private String username;
    private String password;
    private Role role; // CUSTOMER / ADMIN / TELLER / COMPLIANCE_OFFICER / FRAUD_ANALYST
}

