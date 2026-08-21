package com.paystream.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBranchManagerRequest {
    @NotBlank(message = "username is required")
    private String username;

    @NotBlank(message = "password is required")
    private String password;

    private String email;

    @NotBlank(message = "branchCode is required")
    private String branchCode;

    @NotBlank(message = "employeeId is required")
    private String employeeId;
}
