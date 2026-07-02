package com.paystream.transactionservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.transactionservice.entity.Transaction;
import com.paystream.transactionservice.enums.PaymentMode;
import com.paystream.transactionservice.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionEventConsumer unit tests")
class TransactionEventConsumerTest {

    @Mock
    private TransactionRepository transactionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private TransactionEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new TransactionEventConsumer(transactionRepository, objectMapper);
    }

    private String eventJson(String paymentMode, String referenceNumber, String debitAcc, String creditAcc, BigDecimal amount) throws Exception {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId(java.util.UUID.randomUUID().toString())
                .paymentMode(paymentMode)
                .paymentReferenceNumber(referenceNumber)
                .debitAccountNumber(debitAcc)
                .creditAccountNumber(creditAcc)
                .amount(amount)
                .status("COMPLETED")
                .completedAt(Instant.now())
                .build();
        return objectMapper.writeValueAsString(event);
    }

    @Test
    @DisplayName("Should create a ledger transaction when a NEFT completion event is consumed")
    void testConsumeNeftCompletedEvent_createsTransaction() throws Exception {
        // Given
        String payload = eventJson("NEFT", "NEFT202606290001", "1111222233334444", "5555666677778888", new BigDecimal("25000"));
        when(transactionRepository.existsByPaymentReferenceNumber("NEFT202606290001")).thenReturn(false);
        when(transactionRepository.existsByTransactionId(anyString())).thenReturn(false);

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentMode()).isEqualTo(PaymentMode.NEFT);
        assertThat(captor.getValue().getPaymentReferenceNumber()).isEqualTo("NEFT202606290001");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("25000");
    }

    @Test
    @DisplayName("Should create a ledger transaction when an RTGS settlement event is consumed")
    void testConsumeRtgsSettledEvent_createsTransaction() throws Exception {
        // Given
        String payload = eventJson("RTGS", "RTGS202606290002", "1111222233334444", "5555666677778888", new BigDecimal("500000"));
        when(transactionRepository.existsByPaymentReferenceNumber("RTGS202606290002")).thenReturn(false);
        when(transactionRepository.existsByTransactionId(anyString())).thenReturn(false);

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentMode()).isEqualTo(PaymentMode.RTGS);
        assertThat(captor.getValue().getPaymentReferenceNumber()).isEqualTo("RTGS202606290002");
    }

    @Test
    @DisplayName("Should create a ledger transaction when an IMPS completion event is consumed")
    void testConsumeImpsCompletedEvent_createsTransaction() throws Exception {
        // Given
        String payload = eventJson("IMPS", "IMPS202606290003", "1111222233334444", "5555666677778888", new BigDecimal("7500"));
        when(transactionRepository.existsByPaymentReferenceNumber("IMPS202606290003")).thenReturn(false);
        when(transactionRepository.existsByTransactionId(anyString())).thenReturn(false);

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentMode()).isEqualTo(PaymentMode.IMPS);
        assertThat(captor.getValue().getPaymentReferenceNumber()).isEqualTo("IMPS202606290003");
    }

    @Test
    @DisplayName("Should create a ledger transaction when a UPI completion event is consumed")
    void testConsumeUpiCompletedEvent_createsTransaction() throws Exception {
        // Given
        String payload = eventJson("UPI", "UPI202606290004", "1111222233334444", "9999888877776666", new BigDecimal("899"));
        when(transactionRepository.existsByPaymentReferenceNumber("UPI202606290004")).thenReturn(false);
        when(transactionRepository.existsByTransactionId(anyString())).thenReturn(false);

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getPaymentMode()).isEqualTo(PaymentMode.UPI);
        assertThat(captor.getValue().getPaymentReferenceNumber()).isEqualTo("UPI202606290004");
    }

    @Test
    @DisplayName("Should skip creating a duplicate ledger entry when the payment reference was already recorded")
    void testConsumePaymentCompletedEvent_duplicateReference_skipsCreation() throws Exception {
        // Given
        String payload = eventJson("UPI", "UPI202606290005", "1111222233334444", "9999888877776666", new BigDecimal("250"));
        when(transactionRepository.existsByPaymentReferenceNumber("UPI202606290005")).thenReturn(true);

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        verify(transactionRepository, never()).save(any());
    }
}
