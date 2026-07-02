package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.AccountStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountStatusUpdateRequest {

    @NotNull
    private AccountStatus status;

    private String reason;
}
