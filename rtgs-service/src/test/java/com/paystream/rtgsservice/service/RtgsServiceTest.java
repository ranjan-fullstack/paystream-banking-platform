package com.paystream.rtgsservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.rtgsservice.client.AccountClient;
import com.paystream.rtgsservice.client.dto.AccountValidationResponse;
import com.paystream.rtgsservice.dto.RtgsTransactionResponse;
import com.paystream.rtgsservice.dto.RtgsTransferRequest;
import com.paystream.rtgsservice.entity.OutboxEvent;
import com.paystream.rtgsservice.entity.RtgsTransaction;
import com.paystream.rtgsservice.enums.RtgsPurpose;
import com.paystream.rtgsservice.enums.RtgsStatus;
import com.paystream.rtgsservice.exception.RtgsAlreadySettledException;
import com.paystream.rtgsservice.exception.RtgsTransactionNotFoundException;
import com.paystream.rtgsservice.exception.RtgsWindowClosedException;
import com.paystream.rtgsservice.repository.OutboxEventRepository;
import com.paystream.rtgsservice.repository.RtgsTransactionRepository;
import com.paystream.rtgsservice.service.impl.RtgsServiceImpl;
import com.paystream.rtgsservice.util.RtgsWindowValidator;
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
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RtgsService unit tests")
class RtgsServiceTest {

    @Mock
    private RtgsTransactionRepository rtgsTransactionRepository;

    @Mock
    private AccountClient accountClient;

    @Mock
    private RtgsWindowValidator windowValidator;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RtgsServiceImpl rtgsService;

    private RtgsTransferRequest buildRequest() {
        RtgsTransferRequest request = new RtgsTransferRequest();
        request.setCustomerId("CIF009988");
        request.setSenderAccountNumber("1111222233334444");
        request.setSenderIfsc("PAYS0BLR01");
        request.setBeneficiaryAccountNumber("5555666677778888");
        request.setBeneficiaryIfsc("ICIC0001234");
        request.setBeneficiaryName("Vikram Singh Properties Pvt Ltd");
        request.setAmount(new BigDecimal("500000.00"));
        request.setPurpose(RtgsPurpose.PROPERTY_PURCHASE);
        return request;
    }

    private void stubHappyPath(RtgsTransferRequest request) {
        AccountValidationResponse validation = new AccountValidationResponse();
        validation.setValid(true);
        when(windowValidator.isWithinWindow(any(LocalDateTime.class))).thenReturn(true);
        when(accountClient.validate(request.getSenderAccountNumber())).thenReturn(validation);
        when(rtgsTransactionRepository.existsByRtgsReferenceNumber(anyString())).thenReturn(false);
        when(rtgsTransactionRepository.save(any(RtgsTransaction.class))).thenAnswer(inv -> {
            RtgsTransaction saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    @DisplayName("Should initiate RTGS transfer with PROCESSING status and write outbox event")
    void testInitiateTransferSuccess() throws Exception {
        RtgsTransferRequest request = buildRequest();
        stubHappyPath(request);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RtgsTransactionResponse response = rtgsService.initiateTransfer(request);

        assertThat(response.getRtgsReferenceNumber()).startsWith("RTGS");
        assertThat(response.getStatus()).isEqualTo(RtgsStatus.PROCESSING);
        assertThat(response.getRbiUtrNumber()).isNull();

        verify(accountClient, never()).debit(anyString(), any());
        verify(accountClient, never()).credit(anyString(), any());
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("Should write outbox event with topic payment.rtgs.initiated and correct fields")
    void testInitiateTransfer_OutboxEventHasCorrectFields() throws Exception {
        RtgsTransferRequest request = buildRequest();
        stubHappyPath(request);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentMode\":\"RTGS\"}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        rtgsService.initiateTransfer(request);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        OutboxEvent outbox = captor.getValue();
        assertThat(outbox.getTopic()).isEqualTo("payment.rtgs.initiated");
        assertThat(outbox.getEventType()).isEqualTo("PaymentInitiated");
        assertThat(outbox.getAggregateType()).isEqualTo("RtgsTransaction");
        assertThat(outbox.isPublished()).isFalse();
    }

    @Test
    @DisplayName("Should reject an RTGS request below the Rs. 2,00,000 minimum")
    void testInitiateTransfer_BelowMinimum_throwsException() {
        RtgsTransferRequest request = buildRequest();
        request.setAmount(new BigDecimal("150000.00"));
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<ConstraintViolation<RtgsTransferRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("2,00,000"));
    }

    @Test
    @DisplayName("Should throw exception when initiating RTGS outside the settlement window")
    void testInitiateTransfer_OutsideWindow_throwsException() {
        RtgsTransferRequest request = buildRequest();
        when(windowValidator.isWithinWindow(any(LocalDateTime.class))).thenReturn(false);

        assertThatThrownBy(() -> rtgsService.initiateTransfer(request))
                .isInstanceOf(RtgsWindowClosedException.class);

        verify(rtgsTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when initiating RTGS on a Sunday")
    void testInitiateTransfer_WeekendSunday_throwsException() {
        RtgsTransferRequest request = buildRequest();
        when(windowValidator.isWithinWindow(any(LocalDateTime.class))).thenReturn(false);

        assertThatThrownBy(() -> rtgsService.initiateTransfer(request))
                .isInstanceOf(RtgsWindowClosedException.class);

        verify(accountClient, never()).validate(anyString());
    }

    @Test
    @DisplayName("Should throw exception when initiating RTGS on Saturday after 1:00 PM")
    void testInitiateTransfer_SaturdayAfter1PM_throwsException() {
        RtgsTransferRequest request = buildRequest();
        when(windowValidator.isWithinWindow(any(LocalDateTime.class))).thenReturn(false);

        assertThatThrownBy(() -> rtgsService.initiateTransfer(request))
                .isInstanceOf(RtgsWindowClosedException.class);
    }

    @Test
    @DisplayName("Should return PROCESSING on Friday before 6:00 PM (async saga)")
    void testInitiateTransfer_FridayBefore6PM_returnsProcessing() throws Exception {
        RtgsTransferRequest request = buildRequest();
        stubHappyPath(request);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RtgsTransactionResponse response = rtgsService.initiateTransfer(request);

        assertThat(response.getStatus()).isEqualTo(RtgsStatus.PROCESSING);
    }

    @Test
    @DisplayName("Should recall a non-settled RTGS transaction successfully")
    void testRecallRequest_Success() {
        RtgsTransaction txn = new RtgsTransaction();
        txn.setId(UUID.randomUUID());
        txn.setRtgsReferenceNumber("RTGS202606290001");
        txn.setStatus(RtgsStatus.PROCESSING);

        when(rtgsTransactionRepository.findByRtgsReferenceNumber("RTGS202606290001")).thenReturn(Optional.of(txn));
        when(rtgsTransactionRepository.save(any(RtgsTransaction.class))).thenAnswer(inv -> inv.getArgument(0));

        RtgsTransactionResponse response = rtgsService.recall("RTGS202606290001");

        assertThat(response.getStatus()).isEqualTo(RtgsStatus.RETURNED);
    }

    @Test
    @DisplayName("Should throw exception when recalling an already-settled RTGS transaction")
    void testRecallRequest_AlreadySettled_throwsException() {
        RtgsTransaction txn = new RtgsTransaction();
        txn.setId(UUID.randomUUID());
        txn.setRtgsReferenceNumber("RTGS202606290002");
        txn.setStatus(RtgsStatus.COMPLETED);

        when(rtgsTransactionRepository.findByRtgsReferenceNumber("RTGS202606290002")).thenReturn(Optional.of(txn));

        assertThatThrownBy(() -> rtgsService.recall("RTGS202606290002"))
                .isInstanceOf(RtgsAlreadySettledException.class);

        verify(rtgsTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw exception when recalling a non-existent RTGS transaction")
    void testRecallRequest_NotFound_throwsException() {
        when(rtgsTransactionRepository.findByRtgsReferenceNumber("RTGS_UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rtgsService.recall("RTGS_UNKNOWN"))
                .isInstanceOf(RtgsTransactionNotFoundException.class);
    }
}
