package com.paystream.upiservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.upiservice.entity.OutboxEvent;
import com.paystream.upiservice.entity.ProcessedEvent;
import com.paystream.upiservice.entity.UpiTransaction;
import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.repository.OutboxEventRepository;
import com.paystream.upiservice.repository.ProcessedEventRepository;
import com.paystream.upiservice.repository.UpiTransactionRepository;
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
@DisplayName("UpiSagaConsumer unit tests")
class UpiSagaConsumerTest {

    @Mock private UpiTransactionRepository upiTransactionRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks
    private UpiSagaConsumer upiSagaConsumer;

    private static final String REF = "UPI1234567890";

    private UpiTransaction buildTxn() {
        UpiTransaction txn = new UpiTransaction();
        txn.setId(UUID.randomUUID());
        txn.setUpiTransactionId(REF);
        txn.setAmount(new BigDecimal("1000.00"));
        txn.setStatus(UpiTransactionStatus.PROCESSING);
        return txn;
    }

    @BeforeEach
    void neverProcessedByDefault() {
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(any(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("payment.credited for UPI → sets status COMPLETED and writes completion outbox")
    void testOnSagaEvent_Credited_SetsCompleted() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("SENDER001").beneficiaryAccountNumber("BENE001")
                .amount(new BigDecimal("1000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        UpiTransaction txn = buildTxn();
        when(upiTransactionRepository.findByUpiTransactionId(REF)).thenReturn(Optional.of(txn));
        when(upiTransactionRepository.save(any())).thenReturn(txn);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        upiSagaConsumer.onSagaEvent("{}", "payment.credited");

        assertThat(txn.getStatus()).isEqualTo(UpiTransactionStatus.COMPLETED);
        assertThat(txn.getNpciTransactionId()).startsWith("NPCI");
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("payment.credited for non-UPI mode → ignored")
    void testOnSagaEvent_Credited_NonUpi_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("NEFT")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("1000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);

        upiSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(upiTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("payment.debit.failed → sets status FAILED with failure reason")
    void testOnSagaEvent_DebitFailed_SetsFailed() throws Exception {
        PaymentDebitFailedEvent event = PaymentDebitFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("1000.00"))
                .failureReason("Insufficient balance").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentDebitFailedEvent.class))).thenReturn(event);
        UpiTransaction txn = buildTxn();
        when(upiTransactionRepository.findByUpiTransactionId(REF)).thenReturn(Optional.of(txn));
        when(upiTransactionRepository.save(any())).thenReturn(txn);
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        upiSagaConsumer.onSagaEvent("{}", "payment.debit.failed");

        assertThat(txn.getStatus()).isEqualTo(UpiTransactionStatus.FAILED);
        assertThat(txn.getFailureReason()).contains("Insufficient balance");
    }

    @Test
    @DisplayName("payment.reversed → sets status FAILED (saga compensation completed)")
    void testOnSagaEvent_Reversed_SetsFailed() throws Exception {
        PaymentReversedEvent event = PaymentReversedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("S").amount(new BigDecimal("1000.00"))
                .reversedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversedEvent.class))).thenReturn(event);
        UpiTransaction txn = buildTxn();
        when(upiTransactionRepository.findByUpiTransactionId(REF)).thenReturn(Optional.of(txn));
        when(upiTransactionRepository.save(any())).thenReturn(txn);
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        upiSagaConsumer.onSagaEvent("{}", "payment.reversed");

        assertThat(txn.getStatus()).isEqualTo(UpiTransactionStatus.FAILED);
    }

    @Test
    @DisplayName("payment.reversal.failed → sets status RECONCILIATION_REQUIRED")
    void testOnSagaEvent_ReversalFailed_SetsReconciliationRequired() throws Exception {
        PaymentReversalFailedEvent event = PaymentReversalFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("S").amount(new BigDecimal("1000.00"))
                .failureReason("DB timeout").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentReversalFailedEvent.class))).thenReturn(event);
        UpiTransaction txn = buildTxn();
        when(upiTransactionRepository.findByUpiTransactionId(REF)).thenReturn(Optional.of(txn));
        when(upiTransactionRepository.save(any())).thenReturn(txn);
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        upiSagaConsumer.onSagaEvent("{}", "payment.reversal.failed");

        assertThat(txn.getStatus()).isEqualTo(UpiTransactionStatus.RECONCILIATION_REQUIRED);
    }

    @Test
    @DisplayName("idempotency: duplicate payment.credited event is ignored")
    void testOnSagaEvent_DuplicateCredited_Ignored() throws Exception {
        PaymentCreditedEvent event = PaymentCreditedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(new BigDecimal("1000.00")).creditedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditedEvent.class))).thenReturn(event);
        // Mark as already processed
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(REF, "CREDITED")).thenReturn(true);

        upiSagaConsumer.onSagaEvent("{}", "payment.credited");

        verify(upiTransactionRepository, never()).save(any());
        verify(outboxEventRepository, never()).save(any());
    }
}
