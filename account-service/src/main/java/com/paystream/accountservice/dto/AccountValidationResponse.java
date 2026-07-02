package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountValidationResponse {
    private boolean valid;
    private String accountNumber;
    private AccountStatus status;
    private String reason;
}
