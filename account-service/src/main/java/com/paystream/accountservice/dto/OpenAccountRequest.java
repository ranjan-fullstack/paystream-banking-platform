package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenAccountRequest {

    @NotBlank
    private String customerId;

    private Long userId;

    @NotNull
    private AccountType accountType;

    @NotBlank
    private String branchCode;

    private String nomineeName;
}
