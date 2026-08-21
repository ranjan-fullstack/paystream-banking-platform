package com.paystream.accountservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.accountservice.dto.AmountRequest;
import com.paystream.accountservice.entity.OutboxEvent;
import com.paystream.accountservice.entity.ProcessedEvent;
import com.paystream.accountservice.exception.InsufficientBalanceException;
import com.paystream.accountservice.repository.OutboxEventRepository;
import com.paystream.accountservice.repository.ProcessedEventRepository;
import com.paystream.accountservice.service.AccountService;
import com.paystream.commonlib.event.PaymentCreditFailedEvent;
import com.paystream.commonlib.event.PaymentDebitedEvent;
import com.paystream.commonlib.event.PaymentInitiatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PaymentSagaConsumer unit tests")
class PaymentSagaConsumerTest {

    @Mock private AccountService accountService;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private ObjectMapper objectMapper;
    @Mock private SagaCompensationWriter sagaCompensationWriter;

    @InjectMocks
    private PaymentSagaConsumer paymentSagaConsumer;

    private static final String REF = "UPI9876543210";
    private static final BigDecimal AMOUNT = new BigDecimal("500.00");

    @BeforeEach
    void neverProcessedByDefault() {
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(any(), any())).thenReturn(false);
        when(processedEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("onPaymentInitiated: successful debit writes PaymentDebited outbox event")
    void testOnPaymentInitiated_SuccessfulDebit_WritesDebitedEvent() throws Exception {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("SENDER001").beneficiaryAccountNumber("BENE001")
                .amount(AMOUNT).initiatedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentInitiatedEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doNothing().when(accountService).debit(anyString(), any(AmountRequest.class));

        paymentSagaConsumer.onPaymentInitiated("{}");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PaymentDebited");
        assertThat(captor.getValue().getTopic()).isEqualTo("payment.debited");
    }

    @Test
    @DisplayName("onPaymentInitiated: debit failure writes PaymentDebitFailed outbox event")
    void testOnPaymentInitiated_DebitFails_WritesDebitFailedEvent() throws Exception {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("SENDER001").beneficiaryAccountNumber("BENE001")
                .amount(AMOUNT).initiatedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentInitiatedEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doThrow(new InsufficientBalanceException("SENDER001"))
                .when(accountService).debit(anyString(), any(AmountRequest.class));

        paymentSagaConsumer.onPaymentInitiated("{}");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PaymentDebitFailed");
        assertThat(captor.getValue().getTopic()).isEqualTo("payment.debit.failed");
    }

    @Test
    @DisplayName("onPaymentDebited: successful credit writes PaymentCredited outbox event")
    void testOnPaymentDebited_SuccessfulCredit_WritesCreditedEvent() throws Exception {
        PaymentDebitedEvent event = PaymentDebitedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("SENDER001").beneficiaryAccountNumber("BENE001")
                .amount(AMOUNT).debitedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentDebitedEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doNothing().when(accountService).credit(anyString(), any(AmountRequest.class));

        paymentSagaConsumer.onPaymentDebited("{}");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PaymentCredited");
        assertThat(captor.getValue().getTopic()).isEqualTo("payment.credited");
    }

    @Test
    @DisplayName("onPaymentDebited: credit failure delegates to SagaCompensationWriter in its own transaction")
    void testOnPaymentDebited_CreditFails_DelegatesToCompensationWriter() throws Exception {
        PaymentDebitedEvent event = PaymentDebitedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("IMPS")
                .senderAccountNumber("SENDER001").beneficiaryAccountNumber("BENE_MISSING")
                .amount(AMOUNT).debitedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentDebitedEvent.class))).thenReturn(event);
        doThrow(new RuntimeException("Account not found: BENE_MISSING"))
                .when(accountService).credit(anyString(), any(AmountRequest.class));

        paymentSagaConsumer.onPaymentDebited("{}");

        // The compensating write must go through the injected collaborator (so its
        // own @Transactional(REQUIRES_NEW) proxy applies) rather than being written
        // directly here, which would share — and roll back with — the doomed transaction.
        verify(sagaCompensationWriter).recordCreditFailure(eq(event), eq(REF), anyString());
        verify(outboxEventRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("onCreditFailed: successful reversal writes PaymentReversed outbox event")
    void testOnCreditFailed_SuccessfulReversal_WritesReversedEvent() throws Exception {
        PaymentCreditFailedEvent event = PaymentCreditFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("SENDER001").beneficiaryAccountNumber("BENE001")
                .amount(AMOUNT).failureReason("DB error").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditFailedEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doNothing().when(accountService).credit(anyString(), any(AmountRequest.class));

        paymentSagaConsumer.onCreditFailed("{}");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("PaymentReversed");
        assertThat(captor.getValue().getTopic()).isEqualTo("payment.reversed");
    }

    @Test
    @DisplayName("onCreditFailed: reversal failure writes PaymentReversalFailed AND PaymentReconciliationRequired")
    void testOnCreditFailed_ReversalFails_WritesReversalFailedAndReconciliation() throws Exception {
        PaymentCreditFailedEvent event = PaymentCreditFailedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("SENDER001").beneficiaryAccountNumber("BENE001")
                .amount(AMOUNT).failureReason("DB error").failedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentCreditFailedEvent.class))).thenReturn(event);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        doThrow(new RuntimeException("Reversal DB failure"))
                .when(accountService).credit(anyString(), any(AmountRequest.class));

        paymentSagaConsumer.onCreditFailed("{}");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(captor.capture());
        List<OutboxEvent> saved = captor.getAllValues();
        assertThat(saved).anyMatch(e -> "PaymentReversalFailed".equals(e.getEventType()));
        assertThat(saved).anyMatch(e -> "PaymentReconciliationRequired".equals(e.getEventType()));
    }

    @Test
    @DisplayName("idempotency: duplicate INITIATED event is ignored")
    void testOnPaymentInitiated_Duplicate_Ignored() throws Exception {
        PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                .paymentReferenceNumber(REF).paymentMode("UPI")
                .senderAccountNumber("S").beneficiaryAccountNumber("B")
                .amount(AMOUNT).initiatedAt(Instant.now()).build();

        when(objectMapper.readValue(any(String.class), eq(PaymentInitiatedEvent.class))).thenReturn(event);
        when(processedEventRepository.existsByPaymentReferenceNumberAndEventType(REF, "INITIATED")).thenReturn(true);

        paymentSagaConsumer.onPaymentInitiated("{}");

        verify(accountService, never()).debit(any(), any());
        verify(outboxEventRepository, never()).save(any());
    }
}
