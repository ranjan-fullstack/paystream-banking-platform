package com.paystream.accountservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.accountservice.dto.*;
import com.paystream.accountservice.entity.AccountLimit;
import com.paystream.accountservice.entity.BankAccount;
import com.paystream.accountservice.enums.AccountStatus;
import com.paystream.accountservice.enums.AccountType;
import com.paystream.accountservice.enums.PaymentMode;
import com.paystream.accountservice.exception.AccountNotActiveException;
import com.paystream.accountservice.exception.AccountNotFoundException;
import com.paystream.accountservice.exception.InsufficientBalanceException;
import com.paystream.accountservice.repository.AccountLimitRepository;
import com.paystream.accountservice.repository.BankAccountRepository;
import com.paystream.accountservice.service.impl.AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService unit tests")
class AccountServiceTest {

    @Mock
    private BankAccountRepository bankAccountRepository;

    @Mock
    private AccountLimitRepository accountLimitRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AccountServiceImpl accountService;

    private BankAccount account;

    @BeforeEach
    void setUp() {
        account = new BankAccount();
        account.setId(UUID.randomUUID());
        account.setAccountNumber("1234567890123456");
        account.setIfscCode("PAYS0BLR01");
        account.setAccountType(AccountType.SAVINGS);
        account.setBalance(new BigDecimal("5000.00"));
        account.setAvailableBalance(new BigDecimal("5000.00"));
        account.setHoldAmount(BigDecimal.ZERO);
        account.setCurrency("INR");
        account.setStatus(AccountStatus.ACTIVE);
        account.setCustomerId("CIF001234");
        account.setBranchCode("BLR01");
    }

    @Test
    @DisplayName("Should open a new account successfully with default limits seeded")
    void testOpenAccountSuccess() throws Exception {
        // Given
        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("CIF001234");
        request.setAccountType(AccountType.SAVINGS);
        request.setBranchCode("BLR01");
        request.setNomineeName("Anjali Sharma");

        when(bankAccountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        AccountResponse response = accountService.openAccount(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAccountNumber()).hasSize(16);
        assertThat(response.getIfscCode()).isEqualTo("PAYS0BLR01");
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.getCustomerId()).isEqualTo("CIF001234");

        ArgumentCaptor<List<AccountLimit>> limitsCaptor = ArgumentCaptor.forClass(List.class);
        verify(accountLimitRepository).saveAll(limitsCaptor.capture());
        assertThat(limitsCaptor.getValue()).hasSize(4);
        verify(kafkaTemplate).send(eq("account.created"), eq(response.getAccountNumber()), anyString());
    }

    @Test
    @DisplayName("Should fetch account by ID successfully")
    void testGetAccountByIdSuccess() {
        // Given
        when(bankAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // When
        AccountResponse response = accountService.getById(account.getId());

        // Then
        assertThat(response.getAccountNumber()).isEqualTo("1234567890123456");
        assertThat(response.getCustomerId()).isEqualTo("CIF001234");
    }

    @Test
    @DisplayName("Should throw exception when account is not found by ID")
    void testGetAccountNotFound_throwsException() {
        // Given
        UUID missingId = UUID.randomUUID();
        when(bankAccountRepository.findById(missingId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> accountService.getById(missingId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(missingId.toString());
    }

    @Test
    @DisplayName("Should fetch account by account number successfully")
    void testGetAccountByAccountNumber() {
        // Given
        when(bankAccountRepository.findByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));

        // When
        AccountResponse response = accountService.getByAccountNumber("1234567890123456");

        // Then
        assertThat(response.getAccountNumber()).isEqualTo("1234567890123456");
    }

    @Test
    @DisplayName("Should fetch all accounts belonging to a customer")
    void testGetAccountsByCustomerId() {
        // Given
        BankAccount secondAccount = new BankAccount();
        secondAccount.setId(UUID.randomUUID());
        secondAccount.setAccountNumber("6543210987654321");
        secondAccount.setIfscCode("PAYS0BLR01");
        secondAccount.setAccountType(AccountType.CURRENT);
        secondAccount.setBalance(BigDecimal.ZERO);
        secondAccount.setAvailableBalance(BigDecimal.ZERO);
        secondAccount.setHoldAmount(BigDecimal.ZERO);
        secondAccount.setCurrency("INR");
        secondAccount.setStatus(AccountStatus.ACTIVE);
        secondAccount.setCustomerId("CIF001234");
        secondAccount.setBranchCode("BLR01");

        when(bankAccountRepository.findByCustomerId("CIF001234")).thenReturn(List.of(account, secondAccount));

        // When
        List<AccountResponse> responses = accountService.getByCustomerId("CIF001234");

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(AccountResponse::getAccountNumber)
                .containsExactlyInAnyOrder("1234567890123456", "6543210987654321");
    }

    @Test
    @DisplayName("Should freeze an active account successfully")
    void testFreezeAccountSuccess() {
        // Given
        AccountStatusUpdateRequest request = new AccountStatusUpdateRequest();
        request.setStatus(AccountStatus.FROZEN);
        request.setReason("Suspicious activity reported");

        when(bankAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        AccountResponse response = accountService.updateStatus(account.getId(), request);

        // Then
        assertThat(response.getStatus()).isEqualTo(AccountStatus.FROZEN);
    }

    @Test
    @DisplayName("Should debit an active account successfully when balance is sufficient")
    void testDebitAccountSuccess() {
        // Given
        AmountRequest request = new AmountRequest();
        request.setAmount(new BigDecimal("2000.00"));
        request.setReferenceNumber("NEFT-REF-001");

        when(bankAccountRepository.findWithLockByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        accountService.debit("1234567890123456", request);

        // Then
        assertThat(account.getBalance()).isEqualByComparingTo("3000.00");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("3000.00");
        verify(bankAccountRepository).save(account);
    }

    @Test
    @DisplayName("Should throw exception when debiting more than the available balance")
    void testDebitAccount_InsufficientBalance_throwsException() {
        // Given
        AmountRequest request = new AmountRequest();
        request.setAmount(new BigDecimal("10000.00"));
        request.setReferenceNumber("NEFT-REF-002");

        when(bankAccountRepository.findWithLockByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));

        // When & Then
        assertThatThrownBy(() -> accountService.debit("1234567890123456", request))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasMessageContaining("1234567890123456");

        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should credit an active account successfully")
    void testCreditAccountSuccess() {
        // Given
        AmountRequest request = new AmountRequest();
        request.setAmount(new BigDecimal("1500.00"));
        request.setReferenceNumber("NEFT-REF-003");

        when(bankAccountRepository.findWithLockByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        accountService.credit("1234567890123456", request);

        // Then
        assertThat(account.getBalance()).isEqualByComparingTo("6500.00");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("6500.00");
    }

    @Test
    @DisplayName("Should throw exception when debiting a frozen account")
    void testDebitAccount_FrozenAccount_throwsException() {
        // Given
        account.setStatus(AccountStatus.FROZEN);
        AmountRequest request = new AmountRequest();
        request.setAmount(new BigDecimal("100.00"));
        request.setReferenceNumber("NEFT-REF-004");

        when(bankAccountRepository.findWithLockByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));

        // When & Then
        assertThatThrownBy(() -> accountService.debit("1234567890123456", request))
                .isInstanceOf(AccountNotActiveException.class)
                .hasMessageContaining("1234567890123456");

        verify(bankAccountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retry account number generation until a unique number is found")
    void testGenerateAccountNumber_isUnique() throws Exception {
        // Given
        OpenAccountRequest request = new OpenAccountRequest();
        request.setCustomerId("CIF005678");
        request.setAccountType(AccountType.SAVINGS);
        request.setBranchCode("MUM02");

        when(bankAccountRepository.existsByAccountNumber(anyString())).thenReturn(true, false);
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        AccountResponse response = accountService.openAccount(request);

        // Then
        assertThat(response.getAccountNumber()).hasSize(16);
        verify(bankAccountRepository, times(2)).existsByAccountNumber(anyString());
    }

    @Test
    @DisplayName("Should propagate optimistic locking failure on concurrent debit of the same account")
    void testOptimisticLocking_concurrentDebit() {
        // Given
        AmountRequest request = new AmountRequest();
        request.setAmount(new BigDecimal("500.00"));
        request.setReferenceNumber("NEFT-REF-005");

        when(bankAccountRepository.findWithLockByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));
        when(bankAccountRepository.save(any(BankAccount.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(BankAccount.class, account.getId().toString()));

        // When & Then
        assertThatThrownBy(() -> accountService.debit("1234567890123456", request))
                .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Should fetch the balance, available balance and hold amount for an account")
    void testGetBalanceSuccess() {
        // Given
        account.setHoldAmount(new BigDecimal("500.00"));
        when(bankAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        // When
        BalanceResponse response = accountService.getBalance(account.getId());

        // Then
        assertThat(response.getAccountNumber()).isEqualTo("1234567890123456");
        assertThat(response.getBalance()).isEqualByComparingTo("5000.00");
        assertThat(response.getHoldAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Should mark a valid active account number as valid")
    void testValidateAccount_activeAccount_returnsValid() {
        // Given
        when(bankAccountRepository.findByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));

        // When
        AccountValidationResponse response = accountService.validate("1234567890123456");

        // Then
        assertThat(response.isValid()).isTrue();
        assertThat(response.getReason()).isNull();
    }

    @Test
    @DisplayName("Should mark an unknown account number as invalid with a reason")
    void testValidateAccount_unknownAccount_returnsInvalid() {
        // Given
        when(bankAccountRepository.findByAccountNumber("0000000000000000")).thenReturn(Optional.empty());

        // When
        AccountValidationResponse response = accountService.validate("0000000000000000");

        // Then
        assertThat(response.isValid()).isFalse();
        assertThat(response.getReason()).isEqualTo("Account not found");
    }

    @Test
    @DisplayName("Should place a hold on an active account with sufficient available balance")
    void testHoldAccountSuccess() {
        // Given
        AmountRequest request = new AmountRequest();
        request.setAmount(new BigDecimal("1000.00"));
        request.setReferenceNumber("HOLD-REF-001");

        when(bankAccountRepository.findWithLockByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        accountService.hold("1234567890123456", request);

        // Then
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("4000.00");
        assertThat(account.getHoldAmount()).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("Should release a previously held amount back into available balance")
    void testReleaseHoldSuccess() {
        // Given
        account.setAvailableBalance(new BigDecimal("4000.00"));
        account.setHoldAmount(new BigDecimal("1000.00"));
        AmountRequest request = new AmountRequest();
        request.setAmount(new BigDecimal("1000.00"));
        request.setReferenceNumber("HOLD-REF-001");

        when(bankAccountRepository.findWithLockByAccountNumber("1234567890123456")).thenReturn(Optional.of(account));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        accountService.release("1234567890123456", request);

        // Then
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("5000.00");
        assertThat(account.getHoldAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Should fetch the configured payment-mode limits for an account")
    void testGetLimitsSuccess() {
        // Given
        AccountLimit neftLimit = new AccountLimit();
        neftLimit.setId(UUID.randomUUID());
        neftLimit.setAccount(account);
        neftLimit.setTransactionType(PaymentMode.NEFT);
        neftLimit.setDailyLimit(new BigDecimal("1000000"));
        neftLimit.setPerTransactionLimit(new BigDecimal("1000000"));
        neftLimit.setUsedTodayAmount(BigDecimal.ZERO);

        when(bankAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountLimitRepository.findByAccountId(account.getId())).thenReturn(List.of(neftLimit));

        // When
        List<AccountLimitResponse> limits = accountService.getLimits(account.getId());

        // Then
        assertThat(limits).hasSize(1);
        assertThat(limits.get(0).getTransactionType()).isEqualTo(PaymentMode.NEFT);
    }

    @Test
    @DisplayName("Should update an existing payment-mode limit for an account")
    void testUpdateLimitSuccess() {
        // Given
        AccountLimit existingLimit = new AccountLimit();
        existingLimit.setId(UUID.randomUUID());
        existingLimit.setAccount(account);
        existingLimit.setTransactionType(PaymentMode.UPI);
        existingLimit.setDailyLimit(new BigDecimal("100000"));
        existingLimit.setPerTransactionLimit(new BigDecimal("100000"));
        existingLimit.setUsedTodayAmount(BigDecimal.ZERO);

        AccountLimitUpdateRequest request = new AccountLimitUpdateRequest();
        request.setTransactionType(PaymentMode.UPI);
        request.setDailyLimit(new BigDecimal("200000"));
        request.setPerTransactionLimit(new BigDecimal("150000"));

        when(bankAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountLimitRepository.findByAccountIdAndTransactionType(account.getId(), PaymentMode.UPI))
                .thenReturn(Optional.of(existingLimit));
        when(accountLimitRepository.save(any(AccountLimit.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        AccountLimitResponse response = accountService.updateLimit(account.getId(), request);

        // Then
        assertThat(response.getDailyLimit()).isEqualByComparingTo("200000");
        assertThat(response.getPerTransactionLimit()).isEqualByComparingTo("150000");
    }
}
