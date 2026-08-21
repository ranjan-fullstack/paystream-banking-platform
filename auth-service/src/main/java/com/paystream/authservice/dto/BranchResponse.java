package com.paystream.authservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class BranchResponse {
    private UUID id;
    private String branchCode;
    private String branchName;
    private String city;
    private String state;
    private String branchPhone;
    private boolean isActive;
    private LocalDateTime createdAt;
    private String message;
}
