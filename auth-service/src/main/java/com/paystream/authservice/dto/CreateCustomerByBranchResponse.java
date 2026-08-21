package com.paystream.authservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateCustomerByBranchResponse {
    private Long userId;
    private String username;
    private String temporaryPassword;
    private String message;
}
