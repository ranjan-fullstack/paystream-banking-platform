package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.AccountStatus;
import com.paystream.accountservice.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private String ifscCode;
    private AccountType accountType;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private String currency;
    private AccountStatus status;
    private String customerId;
    private Long userId;
    private String branchCode;
    private String nomineeName;
    private LocalDateTime createdAt;
}
