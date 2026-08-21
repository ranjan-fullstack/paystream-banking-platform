package com.paystream.accountservice.client.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserBranchInfoResponse {
    private Long userId;
    private String username;
    private String role;
    private String branchCode;
    private String employeeId;
}
