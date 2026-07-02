package com.paystream.transactionservice.service;

import com.paystream.transactionservice.dto.TransactionResponse;
import com.paystream.transactionservice.dto.TransactionSummaryResponse;
import com.paystream.transactionservice.entity.Transaction;
import com.paystream.transactionservice.enums.PaymentMode;
import com.paystream.transactionservice.enums.TransactionStatus;
import com.paystream.transactionservice.exception.TransactionNotFoundException;
import com.paystream.transactionservice.repository.TransactionRepository;
import com.paystream.transactionservice.service.impl.TransactionServiceImpl;
import com.paystream.transactionservice.util.PdfStatementGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService unit tests")
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private PdfStatementGenerator pdfStatementGenerator;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private Transaction buildTransaction(String txnId, PaymentMode mode, String debitAcc, String creditAcc, BigDecimal amount) {
        Transaction txn = new Transaction();
        txn.setId(UUID.randomUUID());
        txn.setTransactionId(txnId);
        txn.setPaymentMode(mode);
        txn.setPaymentReferenceNumber(txnId + "-REF");
        txn.setDebitAccountNumber(debitAcc);
        txn.setCreditAccountNumber(creditAcc);
        txn.setAmount(amount);
        txn.setCurrency("INR");
        txn.setStatus(TransactionStatus.COMPLETED);
        txn.setInitiatedAt(LocalDateTime.now().minusDays(1));
        txn.setCompletedAt(LocalDateTime.now());
        return txn;
    }

    @Test
    @DisplayName("Should fetch a transaction by its transaction ID successfully")
    void testGetTransactionById_Success() {
        // Given
        Transaction txn = buildTransaction("TXN202606290001", PaymentMode.NEFT, "1111222233334444", "5555666677778888", new BigDecimal("5000"));
        when(transactionRepository.findByTransactionId("TXN202606290001")).thenReturn(Optional.of(txn));

        // When
        TransactionResponse response = transactionService.getByTransactionId("TXN202606290001");

        // Then
        assertThat(response.getTransactionId()).isEqualTo("TXN202606290001");
        assertThat(response.getPaymentMode()).isEqualTo(PaymentMode.NEFT);
    }

    @Test
    @DisplayName("Should throw exception when the transaction ID does not exist")
    void testGetTransactionById_NotFound_throwsException() {
        // Given
        when(transactionRepository.findByTransactionId("TXN_UNKNOWN")).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> transactionService.getByTransactionId("TXN_UNKNOWN"))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("TXN_UNKNOWN");
    }

    @Test
    @DisplayName("Should fetch account history applying the requested filters")
    void testGetAccountHistory_withFilters() {
        // Given
        Transaction txn = buildTransaction("TXN202606290002", PaymentMode.IMPS, "1111222233334444", "5555666677778888", new BigDecimal("2000"));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(txn));

        // When
        List<TransactionResponse> history = transactionService.getByAccount(
                "1111222233334444", LocalDateTime.now().minusDays(7), LocalDateTime.now(), PaymentMode.IMPS);

        // Then
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getTransactionId()).isEqualTo("TXN202606290002");
    }

    @Test
    @DisplayName("Should fetch account history filtered by a specific date range")
    void testGetAccountHistory_byDateRange() {
        // Given
        Transaction txn = buildTransaction("TXN202606290003", PaymentMode.NEFT, "1111222233334444", "5555666677778888", new BigDecimal("1000"));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(txn));
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        LocalDateTime to = LocalDateTime.now();

        // When
        List<TransactionResponse> history = transactionService.getByAccount("1111222233334444", from, to, null);

        // Then
        assertThat(history).hasSize(1);
    }

    @Test
    @DisplayName("Should fetch account history filtered by a specific payment mode")
    void testGetAccountHistory_byPaymentMode() {
        // Given
        Transaction txn = buildTransaction("TXN202606290004", PaymentMode.UPI, "1111222233334444", "5555666677778888", new BigDecimal("899"));
        when(transactionRepository.findAll(any(Specification.class))).thenReturn(List.of(txn));

        // When
        List<TransactionResponse> history = transactionService.getByAccount("1111222233334444", null, null, PaymentMode.UPI);

        // Then
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPaymentMode()).isEqualTo(PaymentMode.UPI);
    }

    @Test
    @DisplayName("Should compute a per-mode transaction summary for an account")
    void testGetSummary_Success() {
        // Given
        Transaction neftTxn = buildTransaction("TXN202606290005", PaymentMode.NEFT, "1111222233334444", "5555666677778888", new BigDecimal("10000"));
        Transaction upiTxn1 = buildTransaction("TXN202606290006", PaymentMode.UPI, "1111222233334444", "9999888877776666", new BigDecimal("500"));
        Transaction upiTxn2 = buildTransaction("TXN202606290007", PaymentMode.UPI, "1111222233334444", "9999888877776666", new BigDecimal("300"));

        when(transactionRepository.findAll(any(Specification.class)))
                .thenReturn(List.of(neftTxn, upiTxn1, upiTxn2));

        // When
        TransactionSummaryResponse summary = transactionService.getSummary("1111222233334444");

        // Then
        assertThat(summary.getTotalTransactions()).isEqualTo(3);
        assertThat(summary.getTotalAmount()).isEqualByComparingTo("10800");
        assertThat(summary.getCountByMode()).containsEntry("UPI", 2L);
        assertThat(summary.getTotalAmountByMode()).containsEntry("UPI", new BigDecimal("800"));
    }
}
