package com.paystream.transactionservice.service.impl;

import com.paystream.transactionservice.dto.TransactionResponse;
import com.paystream.transactionservice.dto.TransactionSummaryResponse;
import com.paystream.transactionservice.entity.Transaction;
import com.paystream.transactionservice.enums.PaymentMode;
import com.paystream.transactionservice.exception.TransactionNotFoundException;
import com.paystream.transactionservice.repository.TransactionRepository;
import com.paystream.transactionservice.repository.TransactionSpecifications;
import com.paystream.transactionservice.service.TransactionService;
import com.paystream.transactionservice.util.PdfStatementGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final PdfStatementGenerator pdfStatementGenerator;

    @Override
    public TransactionResponse getByTransactionId(String transactionId) {
        return toResponse(findOrThrow(transactionId));
    }

    @Override
    public List<TransactionResponse> getByAccount(String accountNumber, LocalDateTime from, LocalDateTime to, PaymentMode mode) {
        return transactionRepository.findAll(TransactionSpecifications.forAccount(accountNumber, from, to, mode))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public byte[] generateStatement(String accountNumber) {
        List<Transaction> transactions = transactionRepository.findAll(
                TransactionSpecifications.forAccount(accountNumber, null, null, null));
        return pdfStatementGenerator.generate(accountNumber, transactions);
    }

    @Override
    public TransactionSummaryResponse getSummary(String accountNumber) {
        List<Transaction> transactions = transactionRepository.findAll(
                TransactionSpecifications.forAccount(accountNumber, null, null, null));

        Map<String, Long> countByMode = new HashMap<>();
        Map<String, BigDecimal> totalByMode = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Transaction txn : transactions) {
            String mode = txn.getPaymentMode().name();
            countByMode.merge(mode, 1L, Long::sum);
            totalByMode.merge(mode, txn.getAmount(), BigDecimal::add);
            totalAmount = totalAmount.add(txn.getAmount());
        }

        return TransactionSummaryResponse.builder()
                .accountNumber(accountNumber)
                .totalTransactions(transactions.size())
                .totalAmount(totalAmount)
                .countByMode(countByMode)
                .totalAmountByMode(totalByMode)
                .build();
    }

    private Transaction findOrThrow(String transactionId) {
        return transactionRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId));
    }

    private TransactionResponse toResponse(Transaction txn) {
        return TransactionResponse.builder()
                .transactionId(txn.getTransactionId())
                .paymentMode(txn.getPaymentMode())
                .paymentReferenceNumber(txn.getPaymentReferenceNumber())
                .debitAccountNumber(txn.getDebitAccountNumber())
                .creditAccountNumber(txn.getCreditAccountNumber())
                .amount(txn.getAmount())
                .currency(txn.getCurrency())
                .status(txn.getStatus())
                .initiatedAt(txn.getInitiatedAt())
                .completedAt(txn.getCompletedAt())
                .description(txn.getDescription())
                .build();
    }
}
