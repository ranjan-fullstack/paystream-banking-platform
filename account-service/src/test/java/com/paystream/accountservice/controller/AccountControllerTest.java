package com.paystream.accountservice.controller;

import com.paystream.accountservice.dto.AccountLimitResponse;
import com.paystream.accountservice.dto.AccountResponse;
import com.paystream.accountservice.dto.BalanceResponse;
import com.paystream.accountservice.enums.AccountStatus;
import com.paystream.accountservice.enums.AccountType;
import com.paystream.accountservice.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@DisplayName("AccountController BOLA ownership tests")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountService accountService;

    private UUID accountId;
    private AccountResponse ownerAccountResponse;
    private BalanceResponse balanceResponse;

    @BeforeEach
    void setUp() {
        accountId = UUID.randomUUID();

        ownerAccountResponse = AccountResponse.builder()
                .id(accountId)
                .accountNumber("1234567890123456")
                .ifscCode("PAYS0BLR01")
                .accountType(AccountType.SAVINGS)
                .balance(new BigDecimal("5000.00"))
                .availableBalance(new BigDecimal("5000.00"))
                .currency("INR")
                .status(AccountStatus.ACTIVE)
                .customerId("CIF001234")
                .userId(42L)
                .branchCode("BLR01")
                .createdAt(LocalDateTime.now())
                .build();

        balanceResponse = BalanceResponse.builder()
                .accountNumber("1234567890123456")
                .balance(new BigDecimal("5000.00"))
                .availableBalance(new BigDecimal("5000.00"))
                .holdAmount(BigDecimal.ZERO)
                .currency("INR")
                .build();
    }

    @Test
    @DisplayName("CUSTOMER with matching userId gets their own balance — 200 OK")
    void testGetBalance_OwnerAccess_Returns200() throws Exception {
        when(accountService.getById(accountId)).thenReturn(ownerAccountResponse);
        when(accountService.getBalance(accountId)).thenReturn(balanceResponse);

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header("X-USER-ID", "42")
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER with mismatched userId is denied another customer's balance — 403 Forbidden")
    void testGetBalance_DifferentCustomer_Returns403() throws Exception {
        when(accountService.getById(accountId)).thenReturn(ownerAccountResponse); // account owned by userId=42

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header("X-USER-ID", "99")   // different user
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN can access any account balance without ownership check — 200 OK")
    void testGetBalance_AdminAccess_AnyAccount_Returns200() throws Exception {
        when(accountService.getBalance(accountId)).thenReturn(balanceResponse);

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header("X-USER-ID", "99")   // does not own the account
                        .header("X-USER-ROLE", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("TELLER can access any account balance without ownership check — 200 OK")
    void testGetBalance_TellerAccess_AnyAccount_Returns200() throws Exception {
        when(accountService.getBalance(accountId)).thenReturn(balanceResponse);

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                        .header("X-USER-ID", "99")   // does not own the account
                        .header("X-USER-ROLE", "TELLER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER accessing their own account details — 200 OK")
    void testGetById_OwnerAccess_Returns200() throws Exception {
        when(accountService.getById(accountId)).thenReturn(ownerAccountResponse);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("X-USER-ID", "42")
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER accessing another customer's account details — 403 Forbidden")
    void testGetById_DifferentCustomer_Returns403() throws Exception {
        when(accountService.getById(accountId)).thenReturn(ownerAccountResponse);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .header("X-USER-ID", "99")
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER accessing their own account limits — 200 OK")
    void testGetLimits_OwnerAccess_Returns200() throws Exception {
        when(accountService.getById(accountId)).thenReturn(ownerAccountResponse);
        when(accountService.getLimits(accountId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/accounts/{id}/limits", accountId)
                        .header("X-USER-ID", "42")
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER accessing another customer's limits — 403 Forbidden")
    void testGetLimits_DifferentCustomer_Returns403() throws Exception {
        when(accountService.getById(accountId)).thenReturn(ownerAccountResponse);

        mockMvc.perform(get("/api/v1/accounts/{id}/limits", accountId)
                        .header("X-USER-ID", "99")
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("CUSTOMER accessing accounts for their own customerId — 200 OK")
    void testGetByCustomerId_OwnerAccess_Returns200() throws Exception {
        when(accountService.getByCustomerId("CIF001234")).thenReturn(List.of(ownerAccountResponse));

        mockMvc.perform(get("/api/v1/accounts/customer/{customerId}", "CIF001234")
                        .header("X-USER-ID", "42")
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER accessing accounts for a different customerId — 403 Forbidden")
    void testGetByCustomerId_DifferentCustomer_Returns403() throws Exception {
        when(accountService.getByCustomerId("CIF001234")).thenReturn(List.of(ownerAccountResponse));

        mockMvc.perform(get("/api/v1/accounts/customer/{customerId}", "CIF001234")
                        .header("X-USER-ID", "99")
                        .header("X-USER-ROLE", "CUSTOMER"))
                .andExpect(status().isForbidden());
    }
}
