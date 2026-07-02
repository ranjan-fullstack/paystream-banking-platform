package com.paystream.rtgsservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.rtgsservice.entity.OutboxEvent;
import com.paystream.rtgsservice.entity.ProcessedEvent;
import com.paystream.rtgsservice.entity.RtgsTransaction;
import com.paystream.rtgsservice.enums.RtgsStatus;
import com.paystream.rtgsservice.repository.OutboxEventRepository;
import com.paystream.rtgsservice.repository.ProcessedEventRepository;
import com.paystream.rtgsservice.repository.RtgsTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RtgsSagaConsumer unit tests")
class RtgsSagaConsumerTest {

    @Mock private RtgsTransactionRepository rtgsTransactionRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private RtgsSagaConsumer rtgsSagaConsumer;

    private static final String REF = "RTGS202606290001";

    private RtgsTransaction buildTxn() {
        RtgsTransaction txn = new RtgsTransaction();
        txn.setId(UUID.randomUUID());
        txn.setRtgsReferenceNumber(REF);
        txn.setSenderAccountNumber("1111222233334444");
        txn.setBeneficiaryAccountNumber("5555666677778888");
        txn.setAmount(new BigDecimal("500000.00"));
        txn.setStatus(RtgsStatus.PROCESSING);
        return txn;
    }

    @BeforeEach
    void neverProcessedByDefault() {
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("payment.credited for RTGS → sets status COMPLETED with rbiUtrNumber, writes settlement outbox")
    void testOnSagaEvent_Credited_SetsCompleted() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("RTGS")
                .senderAccountNumber("1111222233334444").beneficiaryAccountNumber("5555666677778888")
                .amount(new BigDecimal("500000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        RtgsTransaction txn = buildTxn();
        when(rtgsTransactionRepository.findByRtgsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(rtgsTransactionRepository.save(any())).thenReturn(txn);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        rtgsSagaConsumer.onSagaEvent("{}", "payment.credited");

        assertThat(txn.getStatus()).isEqualTo(RtgsStatus.COMPLETED);
        assertThat(txn.getRbiUtrNumber()).startsWith("PAYSR");
        assertThat(txn.getRbiUtrNumber()).hasSize(22);
        assertThat(txn.getSettledAt()).isNotNull();
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("payment.credited for non-RTGS mode → ignored")
    void testOnSagaEvent_Credited_NonRtgs_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("500000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);

        rtgsSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(rtgsTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("payment.debit.failed → sets status FAILED with failure reason")
    void testOnSagaEvent_DebitFailed_SetsFailed() throws Exception {
        PaymentDebitFailedEvent event = PaymentDebitFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("RTGS")
                .senderAccountNumber("1111222233334444").beneficiaryAccountNumber("5555666677778888")
                .amount(new BigDecimal("500000.00"))
                .failureReason("Insufficient balance").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentDebitFailedEvent.class))).thenReturn(event);
        RtgsTransaction txn = buildTxn();
        when(rtgsTransactionRepository.findByRtgsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(rtgsTransactionRepository.save(any())).thenReturn(txn);

        rtgsSagaConsumer.onSagaEvent("{}", "payment.debit.failed");

        assertThat(txn.getStatus()).isEqualTo(RtgsStatus.FAILED);
        assertThat(txn.getFailureReason()).contains("Insufficient balance");
    }

    @Test
    @DisplayName("payment.reversed → sets status FAILED (saga compensation completed)")
    void testOnSagaEvent_Reversed_SetsFailed() throws Exception {
        PaymentReversedEvent event = PaymentReversedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("RTGS")
                .senderAccountNumber("1111222233334444")
                .amount(new BigDecimal("500000.00")).reversedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversedEvent.class))).thenReturn(event);
        RtgsTransaction txn = buildTxn();
        when(rtgsTransactionRepository.findByRtgsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(rtgsTransactionRepository.save(any())).thenReturn(txn);

        rtgsSagaConsumer.onSagaEvent("{}", "payment.reversed");

        assertThat(txn.getStatus()).isEqualTo(RtgsStatus.FAILED);
    }

    @Test
    @DisplayName("payment.reversal.failed → sets status RECONCILIATION_REQUIRED")
    void testOnSagaEvent_ReversalFailed_SetsReconciliationRequired() throws Exception {
        PaymentReversalFailedEvent event = PaymentReversalFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("RTGS")
                .senderAccountNumber("1111222233334444")
                .amount(new BigDecimal("500000.00"))
                .failureReason("DB timeout").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversalFailedEvent.class))).thenReturn(event);
        RtgsTransaction txn = buildTxn();
        when(rtgsTransactionRepository.findByRtgsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(rtgsTransactionRepository.save(any())).thenReturn(txn);

        rtgsSagaConsumer.onSagaEvent("{}", "payment.reversal.failed");

        assertThat(txn.getStatus()).isEqualTo(RtgsStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    @DisplayName("idempotency: duplicate payment.credited event is ignored")
    void testOnSagaEvent_DuplicateCredited_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("RTGS")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("500000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(REF, "CREDITED")).thenReturn(true);

        rtgsSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(rtgsTransactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
