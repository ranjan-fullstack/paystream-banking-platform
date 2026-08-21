package com.paystream.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateBranchRequest {
    @NotBlank(message = "branchCode is required")
    private String branchCode;

    @NotBlank(message = "branchName is required")
    private String branchName;

    private String city;

    private String state;

    private String branchPhone;
}
