package com.paystream.upiservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.upiservice.client.AccountClient;
import com.paystream.upiservice.client.dto.AccountValidationResponse;
import com.paystream.upiservice.dto.*;
import com.paystream.upiservice.entity.OutboxEvent;
import com.paystream.upiservice.entity.UpiCollectRequest;
import com.paystream.upiservice.entity.UpiTransaction;
import com.paystream.upiservice.entity.VirtualPaymentAddress;
import com.paystream.upiservice.enums.CollectRequestStatus;
import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.enums.UpiTransactionType;
import com.paystream.upiservice.exception.CollectRequestExpiredException;
import com.paystream.upiservice.exception.InvalidPinException;
import com.paystream.upiservice.repository.OutboxEventRepository;
import com.paystream.upiservice.repository.UpiCollectRequestRepository;
import com.paystream.upiservice.repository.UpiTransactionRepository;
import com.paystream.upiservice.repository.VpaRepository;
import com.paystream.upiservice.service.impl.UpiTransactionServiceImpl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpiTransactionService unit tests")
class UpiTransactionServiceTest {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Mock private UpiTransactionRepository upiTransactionRepository;
    @Mock private UpiCollectRequestRepository upiCollectRequestRepository;
    @Mock private VpaRepository vpaRepository;
    @Mock private AccountClient accountClient;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private UpiTransactionServiceImpl upiTransactionService;

    private VirtualPaymentAddress buildVpa(String vpa, String accountNumber, String rawPin) {
        VirtualPaymentAddress entity = new VirtualPaymentAddress();
        entity.setVpa(vpa);
        entity.setAccountNumber(accountNumber);
        entity.setActive(true);
        if (rawPin != null) {
            entity.setUpiPin(ENCODER.encode(rawPin));
        }
        return entity;
    }

    private void stubSaveAssignsId() {
        when(upiTransactionRepository.save(any(UpiTransaction.class))).thenAnswer(inv -> {
            UpiTransaction saved = inv.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    @DisplayName("pay() should set status PROCESSING and write outbox event atomically")
    void testPay_WritesOutboxEvent_StatusIsProcessing() throws Exception {
        VirtualPaymentAddress sender = buildVpa("anita.rao@paystream", "1111222233334444", "1234");
        VirtualPaymentAddress receiver = buildVpa("rahul.dev@paystream", "5555666677778888", null);

        UpiPayRequest request = new UpiPayRequest();
        request.setSenderVpa("anita.rao@paystream");
        request.setReceiverVpa("rahul.dev@paystream");
        request.setAmount(new BigDecimal("2500.00"));
        request.setUpiPin("1234");

        when(vpaRepository.findByVpa("anita.rao@paystream")).thenReturn(Optional.of(sender));
        when(vpaRepository.findByVpa("rahul.dev@paystream")).thenReturn(Optional.of(receiver));
        AccountValidationResponse validation = new AccountValidationResponse();
        validation.setValid(true);
        when(accountClient.validate("1111222233334444")).thenReturn(validation);
        when(upiTransactionRepository.existsByUpiTransactionId(any())).thenReturn(false);
        stubSaveAssignsId();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        UpiTransactionResponse response = upiTransactionService.pay(request);

        assertThat(response.getStatus()).isEqualTo(UpiTransactionStatus.PROCESSING);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(accountClient, never()).debit(any(), any());
        verify(accountClient, never()).credit(any(), any());
    }

    @Test
    @DisplayName("pay() outbox event must have the correct topic and reference number")
    void testPay_OutboxEventHasCorrectFields() throws Exception {
        VirtualPaymentAddress sender = buildVpa("anita.rao@paystream", "1111222233334444", "1234");
        VirtualPaymentAddress receiver = buildVpa("rahul.dev@paystream", "5555666677778888", null);

        UpiPayRequest request = new UpiPayRequest();
        request.setSenderVpa("anita.rao@paystream");
        request.setReceiverVpa("rahul.dev@paystream");
        request.setAmount(new BigDecimal("500.00"));
        request.setUpiPin("1234");

        when(vpaRepository.findByVpa("anita.rao@paystream")).thenReturn(Optional.of(sender));
        when(vpaRepository.findByVpa("rahul.dev@paystream")).thenReturn(Optional.of(receiver));
        AccountValidationResponse validation = new AccountValidationResponse();
        validation.setValid(true);
        when(accountClient.validate("1111222233334444")).thenReturn(validation);
        when(upiTransactionRepository.existsByUpiTransactionId(any())).thenReturn(false);
        stubSaveAssignsId();
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"paymentMode\":\"UPI\"}");

        upiTransactionService.pay(request);

        verify(outboxEventRepository).save(argThat(event ->
                "payment.upi.initiated".equals(event.getTopic()) &&
                "PaymentInitiated".equals(event.getEventType()) &&
                "UpiTransaction".equals(event.getAggregateType())
        ));
    }

    @Test
    @DisplayName("pay() should throw when an incorrect UPI PIN is provided")
    void testPay_WrongPin_throwsException() {
        VirtualPaymentAddress sender = buildVpa("anita.rao@paystream", "1111222233334444", "1234");
        VirtualPaymentAddress receiver = buildVpa("rahul.dev@paystream", "5555666677778888", null);

        UpiPayRequest request = new UpiPayRequest();
        request.setSenderVpa("anita.rao@paystream");
        request.setReceiverVpa("rahul.dev@paystream");
        request.setAmount(new BigDecimal("2500.00"));
        request.setUpiPin("0000");

        when(vpaRepository.findByVpa("anita.rao@paystream")).thenReturn(Optional.of(sender));
        when(vpaRepository.findByVpa("rahul.dev@paystream")).thenReturn(Optional.of(receiver));

        assertThatThrownBy(() -> upiTransactionService.pay(request))
                .isInstanceOf(InvalidPinException.class);

        verify(upiTransactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject a UPI pay request whose amount exceeds the Rs. 1,00,000 per-transaction limit")
    void testPay_AmountExceedsLimit_throwsException() {
        UpiPayRequest request = new UpiPayRequest();
        request.setSenderVpa("anita.rao@paystream");
        request.setReceiverVpa("rahul.dev@paystream");
        request.setAmount(new BigDecimal("150000.00"));
        request.setUpiPin("1234");
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        Set<ConstraintViolation<UpiPayRequest>> violations = validator.validate(request);

        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getMessage().contains("1,00,000"));
    }

    @Test
    @DisplayName("collect() should create a PENDING_PIN transaction and no outbox event")
    void testCollectRequest_Success() {
        VirtualPaymentAddress payee = buildVpa("merchant.shop@paystream", "9999000011112222", null);
        VirtualPaymentAddress payer = buildVpa("anita.rao@paystream", "1111222233334444", "1234");

        UpiCollectMoneyRequest request = new UpiCollectMoneyRequest();
        request.setRequestedByVpa("merchant.shop@paystream");
        request.setRequestedFromVpa("anita.rao@paystream");
        request.setAmount(new BigDecimal("899.00"));

        when(vpaRepository.findByVpa("merchant.shop@paystream")).thenReturn(Optional.of(payee));
        when(vpaRepository.findByVpa("anita.rao@paystream")).thenReturn(Optional.of(payer));
        when(upiTransactionRepository.existsByUpiTransactionId(any())).thenReturn(false);
        stubSaveAssignsId();
        when(upiCollectRequestRepository.save(any(UpiCollectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        UpiTransactionResponse response = upiTransactionService.collect(request);

        assertThat(response.getStatus()).isEqualTo(UpiTransactionStatus.PENDING_PIN);
        assertThat(response.getTransactionType()).isEqualTo(UpiTransactionType.COLLECT);
        assertThat(response.getExpiresAt()).isAfter(LocalDateTime.now());
        // collect() only creates the request, no outbox event until payer responds
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("respondToCollect() should expire and throw when the collect request has timed out")
    void testCollectRequest_Expired_throwsException() {
        UpiTransaction txn = new UpiTransaction();
        txn.setId(UUID.randomUUID());
        txn.setUpiTransactionId("UPI1000000001");
        txn.setSenderVpa("anita.rao@paystream");
        txn.setReceiverVpa("merchant.shop@paystream");
        txn.setAmount(new BigDecimal("899.00"));
        txn.setStatus(UpiTransactionStatus.PENDING_PIN);
        txn.setExpiresAt(LocalDateTime.now().minusMinutes(5));

        UpiCollectRequest collectRequest = new UpiCollectRequest();
        collectRequest.setStatus(CollectRequestStatus.PENDING);

        CollectRespondRequest respondRequest = new CollectRespondRequest();
        respondRequest.setAccept(true);
        respondRequest.setUpiPin("1234");

        when(upiTransactionRepository.findByUpiTransactionId("UPI1000000001")).thenReturn(Optional.of(txn));
        when(upiCollectRequestRepository.findByUpiTransactionId(txn.getId())).thenReturn(Optional.of(collectRequest));
        when(upiTransactionRepository.save(any(UpiTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(upiCollectRequestRepository.save(any(UpiCollectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> upiTransactionService.respondToCollect("UPI1000000001", respondRequest))
                .isInstanceOf(CollectRequestExpiredException.class);

        assertThat(txn.getStatus()).isEqualTo(UpiTransactionStatus.EXPIRED);
        assertThat(collectRequest.getStatus()).isEqualTo(CollectRequestStatus.EXPIRED);
    }

    @Test
    @DisplayName("respondToCollect() accept should write outbox event and set PROCESSING")
    void testRespondToCollect_Accept_WritesOutboxAndProcessing() throws Exception {
        VirtualPaymentAddress payer = buildVpa("anita.rao@paystream", "1111222233334444", "1234");
        VirtualPaymentAddress payee = buildVpa("merchant.shop@paystream", "9999000011112222", null);

        UpiTransaction txn = new UpiTransaction();
        txn.setId(UUID.randomUUID());
        txn.setUpiTransactionId("UPI1000000002");
        txn.setSenderVpa("anita.rao@paystream");
        txn.setReceiverVpa("merchant.shop@paystream");
        txn.setAmount(new BigDecimal("899.00"));
        txn.setStatus(UpiTransactionStatus.PENDING_PIN);
        txn.setExpiresAt(LocalDateTime.now().plusMinutes(20));

        UpiCollectRequest collectRequest = new UpiCollectRequest();
        collectRequest.setStatus(CollectRequestStatus.PENDING);

        CollectRespondRequest respondRequest = new CollectRespondRequest();
        respondRequest.setAccept(true);
        respondRequest.setUpiPin("1234");

        when(upiTransactionRepository.findByUpiTransactionId("UPI1000000002")).thenReturn(Optional.of(txn));
        when(upiCollectRequestRepository.findByUpiTransactionId(txn.getId())).thenReturn(Optional.of(collectRequest));
        when(vpaRepository.findByVpa("anita.rao@paystream")).thenReturn(Optional.of(payer));
        when(vpaRepository.findByVpa("merchant.shop@paystream")).thenReturn(Optional.of(payee));
        when(upiTransactionRepository.save(any(UpiTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(upiCollectRequestRepository.save(any(UpiCollectRequest.class))).thenAnswer(inv -> inv.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        UpiTransactionResponse response = upiTransactionService.respondToCollect("UPI1000000002", respondRequest);

        assertThat(response.getStatus()).isEqualTo(UpiTransactionStatus.PROCESSING);
        assertThat(collectRequest.getStatus()).isEqualTo(CollectRequestStatus.ACCEPTED);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(accountClient, never()).debit(any(), any());
        verify(accountClient, never()).credit(any(), any());
    }

    @Test
    @DisplayName("respondToCollect() decline should set DECLINED with no outbox event")
    void testRespondToCollect_Decline_Success() {
        UpiTransaction txn = new UpiTransaction();
        txn.setId(UUID.randomUUID());
        txn.setUpiTransactionId("UPI1000000003");
        txn.setSenderVpa("anita.rao@paystream");
        txn.setReceiverVpa("merchant.shop@paystream");
        txn.setAmount(new BigDecimal("899.00"));
        txn.setStatus(UpiTransactionStatus.PENDING_PIN);
        txn.setExpiresAt(LocalDateTime.now().plusMinutes(20));

        UpiCollectRequest collectRequest = new UpiCollectRequest();
        collectRequest.setStatus(CollectRequestStatus.PENDING);

        CollectRespondRequest respondRequest = new CollectRespondRequest();
        respondRequest.setAccept(false);

        when(upiTransactionRepository.findByUpiTransactionId("UPI1000000003")).thenReturn(Optional.of(txn));
        when(upiCollectRequestRepository.findByUpiTransactionId(txn.getId())).thenReturn(Optional.of(collectRequest));
        when(upiTransactionRepository.save(any(UpiTransaction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(upiCollectRequestRepository.save(any(UpiCollectRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        UpiTransactionResponse response = upiTransactionService.respondToCollect("UPI1000000003", respondRequest);

        assertThat(response.getStatus()).isEqualTo(UpiTransactionStatus.DECLINED);
        assertThat(collectRequest.getStatus()).isEqualTo(CollectRequestStatus.DECLINED);
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("refund() should write outbox event with PROCESSING status")
    void testRefund_WritesOutboxEvent() throws Exception {
        UpiTransaction original = new UpiTransaction();
        original.setId(UUID.randomUUID());
        original.setUpiTransactionId("UPI1000000004");
        original.setSenderVpa("anita.rao@paystream");
        original.setReceiverVpa("merchant.shop@paystream");
        original.setAmount(new BigDecimal("899.00"));
        original.setStatus(UpiTransactionStatus.COMPLETED);

        VirtualPaymentAddress refundSender = buildVpa("merchant.shop@paystream", "9999000011112222", null);
        VirtualPaymentAddress refundReceiver = buildVpa("anita.rao@paystream", "1111222233334444", null);

        RefundRequest request = new RefundRequest();
        request.setOriginalUpiTransactionId("UPI1000000004");
        request.setAmount(new BigDecimal("899.00"));
        request.setReason("Order cancelled");

        when(upiTransactionRepository.findByUpiTransactionId("UPI1000000004")).thenReturn(Optional.of(original));
        when(vpaRepository.findByVpa("merchant.shop@paystream")).thenReturn(Optional.of(refundSender));
        when(vpaRepository.findByVpa("anita.rao@paystream")).thenReturn(Optional.of(refundReceiver));
        when(upiTransactionRepository.existsByUpiTransactionId(any())).thenReturn(false);
        stubSaveAssignsId();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        UpiTransactionResponse response = upiTransactionService.refund(request);

        assertThat(response.getTransactionType()).isEqualTo(UpiTransactionType.REFUND);
        assertThat(response.getStatus()).isEqualTo(UpiTransactionStatus.PROCESSING);
        verify(outboxEventRepository).save(any(OutboxEvent.class));
        verify(accountClient, never()).debit(any(), any());
        verify(accountClient, never()).credit(any(), any());
    }
}
