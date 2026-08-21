package com.paystream.authservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class BranchDetailResponse {
    private UUID id;
    private String branchCode;
    private String branchName;
    private String city;
    private String state;
    private String branchPhone;
    private boolean isActive;
    private LocalDateTime createdAt;
    private List<BranchManagerSummary> managers;
}
