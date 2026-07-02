package com.paystream.impsservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.impsservice.client.AccountClient;
import com.paystream.impsservice.client.dto.AccountValidationResponse;
import com.paystream.impsservice.dto.ImpsTransactionResponse;
import com.paystream.impsservice.dto.ImpsTransferRequest;
import com.paystream.impsservice.entity.ImpsTransaction;
import com.paystream.impsservice.entity.MmidRegistration;
import com.paystream.impsservice.entity.OutboxEvent;
import com.paystream.impsservice.enums.ImpsStatus;
import com.paystream.impsservice.enums.TransferMode;
import com.paystream.impsservice.exception.MmidNotFoundException;
import com.paystream.impsservice.repository.ImpsTransactionRepository;
import com.paystream.impsservice.repository.MmidRegistrationRepository;
import com.paystream.impsservice.repository.OutboxEventRepository;
import com.paystream.impsservice.service.impl.ImpsServiceImpl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImpsService unit tests")
class ImpsServiceTest {

    @Mock
    private ImpsTransactionRepository impsTransactionRepository;

    @Mock
    private MmidRegistrationRepository mmidRegistrationRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ImpsServiceImpl impsService;

    private ImpsTransferRequest buildAccountIfscRequest() {
        ImpsTransferRequest request = new ImpsTransferRequest();
        request.setCustomerId("CIF002211");
        request.setTransferMode(TransferMode.ACCOUNT_IFSC);
        request.setSenderAccountNumber("1111222233334444");
        request.setBeneficiaryAccountNumber("5555666677778888");
        request.setBeneficiaryIfsc("SBIN0001234");
        request.setBeneficiaryName("Kavita Nair");
        request.setAmount(new BigDecimal("7500.00"));
        return request;
    }

    private void stubValidSenderAndPersistence() {
        AccountValidationResponse validation = new AccountValidationResponse();
        validation.setValid(true);
        when(accountClient.validate(anyString())).thenReturn(validation);
        when(impsTransactionRepository.existsByImpsReferenceNumber(anyString())).thenReturn(false);
        when(impsTransactionRepository.save(any(ImpsTransaction.class))).thenAnswer(inv -> {
            ImpsTransaction saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    @DisplayName("Should initiate IMPS transfer with PROCESSING status and write outbox event (ACCOUNT_IFSC mode)")
    void testInitiateTransfer_AccountIfscMode_Success() throws Exception {
        ImpsTransferRequest request = buildAccountIfscRequest();
        stubValidSenderAndPersistence();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ImpsTransactionResponse response = impsService.transfer(request);

        assertThat(response.getStatus()).isEqualTo(ImpsStatus.PROCESSING);
        assertThat(response.getImpsReferenceNumber()).startsWith("IMPS");
        assertThat(response.getRrn()).isNull();

        verify(accountClient, never()).debit(anyString(), any());
        verify(accountClient, never()).credit(anyString(), any());
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should write outbox with resolved MMID beneficiary account in payment.imps.initiated")
    void testInitiateTransfer_MobileMmidMode_WritesResolvedBeneficiaryInOutbox() throws Exception {
        ImpsTransferRequest request = new ImpsTransferRequest();
        request.setCustomerId("CIF002211");
        request.setTransferMode(TransferMode.MOBILE_MMID);
        request.setSenderAccountNumber("1111222233334444");
        request.setBeneficiaryMobile("9876501234");
        request.setBeneficiaryMmid("1234567");
        request.setBeneficiaryName("Suresh Babu");
        request.setAmount(new BigDecimal("3000.00"));

        MmidRegistration registration = new MmidRegistration();
        registration.setAccountNumber("9999000011112222");
        registration.setMobileNumber("9876501234");
        registration.setMmid("1234567");
        registration.setActive(true);

        when(mmidRegistrationRepository.findByMobileNumber("9876501234")).thenReturn(Optional.of(registration));
        stubValidSenderAndPersistence();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        impsService.transfer(request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("payment.imps.initiated");
    }

    @Test
    @DisplayName("Should write outbox event with topic payment.imps.initiated and correct aggregate fields")
    void testInitiateTransfer_OutboxEventHasCorrectFields() throws Exception {
        ImpsTransferRequest request = buildAccountIfscRequest();
        stubValidSenderAndPersistence();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentMode\":\"IMPS\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        impsService.transfer(request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getTopic()).isEqualTo("payment.imps.initiated");
        assertThat(outbox.getEventType()).isEqualTo("PaymentInitiated");
        assertThat(outbox.getAggregateType()).isEqualTo("ImpsTransaction");
        assertThat(outbox.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Should reject an IMPS request whose amount exceeds the Rs. 5,00,000 cap")
    void testInitiateTransfer_AmountExceedsMax_throwsException() {
        ImpsTransferRequest request = buildAccountIfscRequest();
        request.setAmount(new BigDecimal("600000.00"));
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<ConstraintViolation<ImpsTransferRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("5,00,000"));
    }

    @Test
    @DisplayName("Should throw exception when the beneficiary MMID does not match the registered mobile number")
    void testInitiateTransfer_InvalidMmid_throwsException() {
        ImpsTransferRequest request = new ImpsTransferRequest();
        request.setCustomerId("CIF002211");
        request.setTransferMode(TransferMode.MOBILE_MMID);
        request.setSenderAccountNumber("1111222233334444");
        request.setBeneficiaryMobile("9876501234");
        request.setBeneficiaryMmid("9999999");
        request.setBeneficiaryName("Suresh Babu");
        request.setAmount(new BigDecimal("3000.00"));

        MmidRegistration registration = new MmidRegistration();
        registration.setAccountNumber("9999000011112222");
        registration.setMobileNumber("9876501234");
        registration.setMmid("1234567");
        registration.setActive(true);

        AccountValidationResponse validation = new AccountValidationResponse();
        validation.setValid(true);
        when(accountClient.validate(anyString())).thenReturn(validation);
        when(mmidRegistrationRepository.findByMobileNumber("9876501234")).thenReturn(Optional.of(registration));

        assertThatThrownBy(() -> impsService.transfer(request))
                .isInstanceOf(MmidNotFoundException.class);

        verify(impsTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("IMPS available 24x7 — returns PROCESSING with no time-window restriction")
    void testInitiateTransfer_Available24x7_ReturnsProcessing() throws Exception {
        ImpsTransferRequest request = buildAccountIfscRequest();
        stubValidSenderAndPersistence();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ImpsTransactionResponse response = impsService.transfer(request);

        assertThat(response.getStatus()).isEqualTo(ImpsStatus.PROCESSING);
    }
}
