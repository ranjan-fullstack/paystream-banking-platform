package com.paystream.accountservice.dto;

import com.paystream.accountservice.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class OpenAccountByBranchRequest {

    // Branch managers only ever have the customer's numeric auth-service User.id on
    // hand (from the create-customer response) — they have no CIF-formatted
    // customerId to type in, and letting them type anything here is how accounts
    // ended up with no userId set (see BankAccount.userId) or a customerId that was
    // actually someone's username.
    @NotNull(message = "userId is required")
    private Long userId;

    @NotNull(message = "accountType is required")
    private AccountType accountType;

    @NotBlank(message = "branchCode is required")
    private String branchCode;

    @NotNull(message = "initialDeposit is required")
    @PositiveOrZero(message = "initialDeposit cannot be negative")
    private BigDecimal initialDeposit;
}
