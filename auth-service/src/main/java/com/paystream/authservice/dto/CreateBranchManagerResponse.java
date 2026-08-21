package com.paystream.authservice.dto;

import com.paystream.authservice.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateBranchManagerResponse {
    private String username;
    private Role role;
    private String branchCode;
    private String employeeId;
}
