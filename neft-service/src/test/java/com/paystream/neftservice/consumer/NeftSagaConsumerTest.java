package com.paystream.neftservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.neftservice.entity.NeftTransaction;
import com.paystream.neftservice.entity.OutboxEvent;
import com.paystream.neftservice.entity.ProcessedEvent;
import com.paystream.neftservice.enums.NeftStatus;
import com.paystream.neftservice.repository.NeftTransactionRepository;
import com.paystream.neftservice.repository.OutboxEventRepository;
import com.paystream.neftservice.repository.ProcessedEventRepository;
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
@DisplayName("NeftSagaConsumer unit tests")
class NeftSagaConsumerTest {

    @Mock private NeftTransactionRepository neftTransactionRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private NeftSagaConsumer neftSagaConsumer;

    private static final String REF = "NEFT202606290001";

    private NeftTransaction buildTxn() {
        NeftTransaction txn = new NeftTransaction();
        txn.setId(UUID.randomUUID());
        txn.setNeftReferenceNumber(REF);
        txn.setSenderAccountNumber("1111222233334444");
        txn.setBeneficiaryAccountNumber("5555666677778888");
        txn.setAmount(new BigDecimal("10000.00"));
        txn.setStatus(NeftStatus.BATCH_PROCESSING);
        return txn;
    }

    @BeforeEach
    void neverProcessedByDefault() {
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("payment.credited for NEFT → sets status COMPLETED with completedAt, writes completion outbox")
    void testOnSagaEvent_Credited_SetsCompleted() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("NEFT")
                .senderAccountNumber("1111222233334444").beneficiaryAccountNumber("5555666677778888")
                .amount(new BigDecimal("10000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        NeftTransaction txn = buildTxn();
        when(neftTransactionRepository.findByNeftReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(neftTransactionRepository.save(any())).thenReturn(txn);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        neftSagaConsumer.onSagaEvent("{}", "payment.credited");

        assertThat(txn.getStatus()).isEqualTo(NeftStatus.COMPLETED);
        assertThat(txn.getCompletedAt()).isNotNull();
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("payment.credited for non-NEFT mode → ignored")
    void testOnSagaEvent_Credited_NonNeft_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("RTGS")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("10000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);

        neftSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(neftTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("payment.debit.failed → sets status FAILED with failure reason")
    void testOnSagaEvent_DebitFailed_SetsFailed() throws Exception {
        PaymentDebitFailedEvent event = PaymentDebitFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("NEFT")
                .senderAccountNumber("1111222233334444").beneficiaryAccountNumber("5555666677778888")
                .amount(new BigDecimal("10000.00"))
                .failureReason("Insufficient balance").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentDebitFailedEvent.class))).thenReturn(event);
        NeftTransaction txn = buildTxn();
        when(neftTransactionRepository.findByNeftReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(neftTransactionRepository.save(any())).thenReturn(txn);

        neftSagaConsumer.onSagaEvent("{}", "payment.debit.failed");

        assertThat(txn.getStatus()).isEqualTo(NeftStatus.FAILED);
        assertThat(txn.getFailureReason()).contains("Insufficient balance");
    }

    @Test
    @DisplayName("payment.reversed → sets status FAILED (saga compensation completed)")
    void testOnSagaEvent_Reversed_SetsFailed() throws Exception {
        PaymentReversedEvent event = PaymentReversedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("NEFT")
                .senderAccountNumber("1111222233334444")
                .amount(new BigDecimal("10000.00")).reversedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversedEvent.class))).thenReturn(event);
        NeftTransaction txn = buildTxn();
        when(neftTransactionRepository.findByNeftReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(neftTransactionRepository.save(any())).thenReturn(txn);

        neftSagaConsumer.onSagaEvent("{}", "payment.reversed");

        assertThat(txn.getStatus()).isEqualTo(NeftStatus.FAILED);
    }

    @Test
    @DisplayName("payment.reversal.failed → sets status RECONCILIATION_REQUIRED")
    void testOnSagaEvent_ReversalFailed_SetsReconciliationRequired() throws Exception {
        PaymentReversalFailedEvent event = PaymentReversalFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("NEFT")
                .senderAccountNumber("1111222233334444")
                .amount(new BigDecimal("10000.00"))
                .failureReason("DB timeout").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversalFailedEvent.class))).thenReturn(event);
        NeftTransaction txn = buildTxn();
        when(neftTransactionRepository.findByNeftReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(neftTransactionRepository.save(any())).thenReturn(txn);

        neftSagaConsumer.onSagaEvent("{}", "payment.reversal.failed");

        assertThat(txn.getStatus()).isEqualTo(NeftStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    @DisplayName("idempotency: duplicate payment.credited event is ignored")
    void testOnSagaEvent_DuplicateCredited_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("NEFT")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("10000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(REF, "CREDITED")).thenReturn(true);

        neftSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(neftTransactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
