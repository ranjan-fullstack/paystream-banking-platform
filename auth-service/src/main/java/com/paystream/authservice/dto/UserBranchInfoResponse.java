package com.paystream.authservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserBranchInfoResponse {
    private Long userId;
    private String username;
    private String role;
    private String branchCode;
    private String employeeId;
}
