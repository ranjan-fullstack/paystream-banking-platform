package com.paystream.authservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class BranchManagerSummary {
    private Long userId;
    private String username;
    private String employeeId;
}
