package com.paystream.impsservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.impsservice.entity.ImpsTransaction;
import com.paystream.impsservice.entity.OutboxEvent;
import com.paystream.impsservice.entity.ProcessedEvent;
import com.paystream.impsservice.enums.ImpsStatus;
import com.paystream.impsservice.repository.ImpsTransactionRepository;
import com.paystream.impsservice.repository.OutboxEventRepository;
import com.paystream.impsservice.repository.ProcessedEventRepository;
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
@DisplayName("ImpsSagaConsumer unit tests")
class ImpsSagaConsumerTest {

    @Mock private ImpsTransactionRepository impsTransactionRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private ImpsSagaConsumer impsSagaConsumer;

    private static final String REF = "IMPS1234567890001";

    private ImpsTransaction buildTxn() {
        ImpsTransaction txn = new ImpsTransaction();
        txn.setId(UUID.randomUUID());
        txn.setImpsReferenceNumber(REF);
        txn.setSenderAccountNumber("1111222233334444");
        txn.setBeneficiaryAccountNumber("5555666677778888");
        txn.setAmount(new BigDecimal("7500.00"));
        txn.setStatus(ImpsStatus.PROCESSING);
        return txn;
    }

    @BeforeEach
    void neverProcessedByDefault() {
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("payment.credited for IMPS → sets status COMPLETED with rrn and completedAt, writes completion outbox")
    void testOnSagaEvent_Credited_SetsCompleted() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("IMPS")
                .senderAccountNumber("1111222233334444").beneficiaryAccountNumber("5555666677778888")
                .amount(new BigDecimal("7500.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        ImpsTransaction txn = buildTxn();
        when(impsTransactionRepository.findByImpsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(impsTransactionRepository.save(any())).thenReturn(txn);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        impsSagaConsumer.onSagaEvent("{}", "payment.credited");

        assertThat(txn.getStatus()).isEqualTo(ImpsStatus.COMPLETED);
        assertThat(txn.getRrn()).matches("\\d{12}");
        assertThat(txn.getCompletedAt()).isNotNull();
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("payment.credited for non-IMPS mode → ignored")
    void testOnSagaEvent_Credited_NonImps_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("7500.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);

        impsSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(impsTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("payment.debit.failed → sets status FAILED with failure reason")
    void testOnSagaEvent_DebitFailed_SetsFailed() throws Exception {
        PaymentDebitFailedEvent event = PaymentDebitFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("IMPS")
                .senderAccountNumber("1111222233334444").beneficiaryAccountNumber("5555666677778888")
                .amount(new BigDecimal("7500.00"))
                .failureReason("Insufficient balance").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentDebitFailedEvent.class))).thenReturn(event);
        ImpsTransaction txn = buildTxn();
        when(impsTransactionRepository.findByImpsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(impsTransactionRepository.save(any())).thenReturn(txn);

        impsSagaConsumer.onSagaEvent("{}", "payment.debit.failed");

        assertThat(txn.getStatus()).isEqualTo(ImpsStatus.FAILED);
        assertThat(txn.getFailureReason()).contains("Insufficient balance");
    }

    @Test
    @DisplayName("payment.reversed → sets status FAILED (saga compensation completed)")
    void testOnSagaEvent_Reversed_SetsFailed() throws Exception {
        PaymentReversedEvent event = PaymentReversedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("IMPS")
                .senderAccountNumber("1111222233334444")
                .amount(new BigDecimal("7500.00")).reversedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversedEvent.class))).thenReturn(event);
        ImpsTransaction txn = buildTxn();
        when(impsTransactionRepository.findByImpsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(impsTransactionRepository.save(any())).thenReturn(txn);

        impsSagaConsumer.onSagaEvent("{}", "payment.reversed");

        assertThat(txn.getStatus()).isEqualTo(ImpsStatus.FAILED);
    }

    @Test
    @DisplayName("payment.reversal.failed → sets status RECONCILIATION_REQUIRED")
    void testOnSagaEvent_ReversalFailed_SetsReconciliationRequired() throws Exception {
        PaymentReversalFailedEvent event = PaymentReversalFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("IMPS")
                .senderAccountNumber("1111222233334444")
                .amount(new BigDecimal("7500.00"))
                .failureReason("DB timeout").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversalFailedEvent.class))).thenReturn(event);
        ImpsTransaction txn = buildTxn();
        when(impsTransactionRepository.findByImpsReferenceNumber(REF)).thenReturn(Optional.of(txn));
        when(impsTransactionRepository.save(any())).thenReturn(txn);

        impsSagaConsumer.onSagaEvent("{}", "payment.reversal.failed");

        assertThat(txn.getStatus()).isEqualTo(ImpsStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    @DisplayName("idempotency: duplicate payment.credited event is ignored")
    void testOnSagaEvent_DuplicateCredited_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("IMPS")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("7500.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(REF, "CREDITED")).thenReturn(true);

        impsSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(impsTransactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
