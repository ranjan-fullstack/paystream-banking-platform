package com.paystream.neftservice.service;

import com.paystream.neftservice.client.AccountClient;
import com.paystream.neftservice.client.dto.AccountValidationResponse;
import com.paystream.neftservice.client.dto.PaymentRailConfigResponse;
import com.paystream.neftservice.dto.NeftTransactionResponse;
import com.paystream.neftservice.dto.NeftTransferRequest;
import com.paystream.neftservice.entity.NeftTransaction;
import com.paystream.neftservice.enums.NeftStatus;
import com.paystream.neftservice.exception.InvalidAccountException;
import com.paystream.neftservice.exception.NeftTransactionNotFoundException;
import com.paystream.neftservice.exception.NeftWindowClosedException;
import com.paystream.neftservice.exception.ServiceUnavailableException;
import com.paystream.neftservice.repository.NeftBatchRepository;
import com.paystream.neftservice.repository.NeftTransactionRepository;
import com.paystream.neftservice.service.impl.NeftServiceImpl;
import com.paystream.neftservice.util.NeftWindowValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NeftService unit tests")
class NeftServiceTest {

    @Mock
    private NeftTransactionRepository neftTransactionRepository;

    @Mock
    private NeftBatchRepository neftBatchRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private NeftWindowValidator windowValidator;

    @InjectMocks
    private NeftServiceImpl neftService;

    private NeftTransferRequest buildRequest() {
        NeftTransferRequest request = new NeftTransferRequest();
        request.setCustomerId("CIF001234");
        request.setSenderAccountNumber("1111222233334444");
        request.setSenderIfsc("PAYS0BLR01");
        request.setBeneficiaryAccountNumber("5555666677778888");
        request.setBeneficiaryIfsc("HDFC0001234");
        request.setBeneficiaryName("Rohit Agarwal");
        request.setAmount(new BigDecimal("25000.00"));
        request.setRemarks("Rent payment");
        return request;
    }

    // validatePaymentRail() runs before window/account checks in
    // NeftServiceImpl.initiateTransfer(), so every test that calls
    // initiateTransfer() needs this mocked -- regardless of what that
    // particular test is actually exercising -- or it NPEs on the
    // unstubbed config before ever reaching the behavior under test.
    private PaymentRailConfigResponse buildEnabledConfig() {
        PaymentRailConfigResponse config = new PaymentRailConfigResponse();
        config.setEnabled(true);
        config.setPerTransactionLimit(new BigDecimal("1000000.00"));
        config.setDailyLimit(new BigDecimal("500000.00"));
        config.setUsedToday(BigDecimal.ZERO);
        config.setRemainingToday(new BigDecimal("500000.00"));
        return config;
    }

    @Test
    @DisplayName("Should initiate a NEFT transfer successfully when the window is open and sender account is valid")
    void testInitiateTransferSuccess() {
        // Given
        NeftTransferRequest request = buildRequest();
        AccountValidationResponse validation = new AccountValidationResponse();
        validation.setValid(true);
        validation.setAccountNumber(request.getSenderAccountNumber());

        when(accountClient.getPaymentConfig(anyString(), anyString())).thenReturn(buildEnabledConfig());
        when(windowValidator.isWithinWindow(any(LocalDateTime.class))).thenReturn(true);
        when(accountClient.validate(request.getSenderAccountNumber())).thenReturn(validation);
        when(neftTransactionRepository.existsByNeftReferenceNumber(anyString())).thenReturn(false);
        when(neftTransactionRepository.save(any(NeftTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        NeftTransactionResponse response = neftService.initiateTransfer(request);

        // Then
        assertThat(response.getNeftReferenceNumber()).startsWith("NEFT");
        assertThat(response.getStatus()).isEqualTo(NeftStatus.QUEUED);
        assertThat(response.getAmount()).isEqualByComparingTo("25000.00");
        assertThat(response.getBeneficiaryName()).isEqualTo("Rohit Agarwal");
    }

    @Test
    @DisplayName("Should throw exception when initiating a NEFT transfer outside the settlement window")
    void testInitiateTransfer_OutsideWindow_throwsException() {
        // Given
        NeftTransferRequest request = buildRequest();
        when(accountClient.getPaymentConfig(anyString(), anyString())).thenReturn(buildEnabledConfig());
        when(windowValidator.isWithinWindow(any(LocalDateTime.class))).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> neftService.initiateTransfer(request))
                .isInstanceOf(NeftWindowClosedException.class);

        verify(accountClient, never()).validate(anyString());
        verify(neftTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a NEFT request whose amount exceeds the Rs. 10,00,000 per-transaction cap")
    void testInitiateTransfer_AmountExceedsMax_throwsException() {
        // Given - Bean Validation enforces the cap declaratively on the DTO (@DecimalMax),
        // it is not re-checked inside NeftServiceImpl, so we validate the constraint directly.
        NeftTransferRequest request = buildRequest();
        request.setAmount(new BigDecimal("1500000"));
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        // When
        Set<ConstraintViolation<NeftTransferRequest>> violations = validator.validate(request);

        // Then
        assertThat(violations).isNotEmpty();
        assertThat(violations)
                .anyMatch(v -> v.getMessage().contains("10,00,000"));
    }

    @Test
    @DisplayName("Should throw exception when the sender account fails validation")
    void testInitiateTransfer_InvalidSenderAccount_throwsException() {
        // Given
        NeftTransferRequest request = buildRequest();
        AccountValidationResponse validation = new AccountValidationResponse();
        validation.setValid(false);
        validation.setReason("Account is FROZEN");

        when(accountClient.getPaymentConfig(anyString(), anyString())).thenReturn(buildEnabledConfig());
        when(windowValidator.isWithinWindow(any(LocalDateTime.class))).thenReturn(true);
        when(accountClient.validate(request.getSenderAccountNumber())).thenReturn(validation);

        // When & Then
        assertThatThrownBy(() -> neftService.initiateTransfer(request))
                .isInstanceOf(InvalidAccountException.class)
                .hasMessageContaining("FROZEN");

        verify(neftTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ServiceUnavailableException when account-service returns an empty payment rail config")
    void testInitiateTransfer_PaymentRailConfigNull_throwsException() {
        // Given -- a successful Feign call that comes back empty is a distinct
        // failure mode from the circuit breaker (which handles account-service
        // being unreachable entirely, not a 200 with no usable body).
        NeftTransferRequest request = buildRequest();
        when(accountClient.getPaymentConfig(anyString(), anyString())).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> neftService.initiateTransfer(request))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining(request.getSenderAccountNumber());

        verify(windowValidator, never()).isWithinWindow(any());
        verify(neftTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fetch a NEFT transaction by its reference number")
    void testGetTransactionByReferenceNumber() {
        // Given
        NeftTransaction txn = new NeftTransaction();
        txn.setNeftReferenceNumber("NEFT202606290001");
        txn.setSenderAccountNumber("1111222233334444");
        txn.setBeneficiaryAccountNumber("5555666677778888");
        txn.setBeneficiaryName("Rohit Agarwal");
        txn.setAmount(new BigDecimal("25000.00"));
        txn.setStatus(NeftStatus.COMPLETED);

        when(neftTransactionRepository.findByNeftReferenceNumber("NEFT202606290001"))
                .thenReturn(Optional.of(txn));

        // When
        NeftTransactionResponse response = neftService.trackStatus("NEFT202606290001");

        // Then
        assertThat(response.getNeftReferenceNumber()).isEqualTo("NEFT202606290001");
        assertThat(response.getStatus()).isEqualTo(NeftStatus.COMPLETED);
    }

    @Test
    @DisplayName("Should throw exception when tracking a NEFT transaction that does not exist")
    void testGetTransactionNotFound_throwsException() {
        // Given
        when(neftTransactionRepository.findByNeftReferenceNumber("NEFT_UNKNOWN"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> neftService.trackStatus("NEFT_UNKNOWN"))
                .isInstanceOf(NeftTransactionNotFoundException.class)
                .hasMessageContaining("NEFT_UNKNOWN");
    }

    @Test
    @DisplayName("Should fetch a customer's NEFT transaction history ordered by most recent")
    void testGetHistorySuccess() {
        // Given
        NeftTransaction txn = new NeftTransaction();
        txn.setNeftReferenceNumber("NEFT202606290002");
        txn.setCustomerId("CIF001234");
        txn.setSenderAccountNumber("1111222233334444");
        txn.setBeneficiaryAccountNumber("5555666677778888");
        txn.setBeneficiaryName("Rohit Agarwal");
        txn.setAmount(new BigDecimal("25000.00"));
        txn.setStatus(NeftStatus.COMPLETED);

        when(neftTransactionRepository.findByCustomerIdOrderByInitiatedAtDesc("CIF001234")).thenReturn(List.of(txn));

        // When
        List<NeftTransactionResponse> history = neftService.getHistory("CIF001234");

        // Then
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getNeftReferenceNumber()).isEqualTo("NEFT202606290002");
    }

    @Test
    @DisplayName("Should fetch all NEFT settlement batches")
    void testGetBatchesSuccess() {
        // Given
        com.paystream.neftservice.entity.NeftBatch batch = new com.paystream.neftservice.entity.NeftBatch();
        batch.setBatchNumber("BATCH-20260629-0830");
        batch.setTotalTransactions(5);
        batch.setSuccessCount(5);
        batch.setFailureCount(0);
        batch.setTotalAmount(new BigDecimal("100000"));
        batch.setStatus(com.paystream.neftservice.enums.NeftBatchStatus.COMPLETED);

        when(neftBatchRepository.findAll()).thenReturn(List.of(batch));

        // When
        List<com.paystream.neftservice.dto.NeftBatchResponse> batches = neftService.getBatches();

        // Then
        assertThat(batches).hasSize(1);
        assertThat(batches.get(0).getBatchNumber()).isEqualTo("BATCH-20260629-0830");
    }

    @Test
    @DisplayName("Should fetch details of a specific NEFT batch by batch number")
    void testGetBatchDetailsSuccess() {
        // Given
        com.paystream.neftservice.entity.NeftBatch batch = new com.paystream.neftservice.entity.NeftBatch();
        batch.setBatchNumber("BATCH-20260629-0830");
        batch.setTotalTransactions(5);
        batch.setSuccessCount(5);
        batch.setFailureCount(0);
        batch.setTotalAmount(new BigDecimal("100000"));
        batch.setStatus(com.paystream.neftservice.enums.NeftBatchStatus.COMPLETED);

        when(neftBatchRepository.findByBatchNumber("BATCH-20260629-0830")).thenReturn(Optional.of(batch));

        // When
        com.paystream.neftservice.dto.NeftBatchResponse response = neftService.getBatchDetails("BATCH-20260629-0830");

        // Then
        assertThat(response.getBatchNumber()).isEqualTo("BATCH-20260629-0830");
        assertThat(response.getSuccessCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("Should throw exception when fetching details of a non-existent NEFT batch")
    void testGetBatchDetails_NotFound_throwsException() {
        // Given
        when(neftBatchRepository.findByBatchNumber("BATCH-UNKNOWN")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> neftService.getBatchDetails("BATCH-UNKNOWN"))
                .isInstanceOf(NeftTransactionNotFoundException.class);
    }
}
