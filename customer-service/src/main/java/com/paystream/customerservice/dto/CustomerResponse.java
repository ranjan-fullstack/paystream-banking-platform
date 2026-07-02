package com.paystream.customerservice.dto;

import com.paystream.customerservice.enums.KycStatus;
import com.paystream.customerservice.enums.RiskRating;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class CustomerResponse {
    private UUID id;
    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String mobile;
    private KycStatus kycStatus;
    private RiskRating riskRating;
    private LocalDateTime createdAt;
}
