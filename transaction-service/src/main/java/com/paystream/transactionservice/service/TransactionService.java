package com.paystream.transactionservice.service;

import com.paystream.transactionservice.dto.TransactionResponse;
import com.paystream.transactionservice.dto.TransactionSummaryResponse;
import com.paystream.transactionservice.enums.PaymentMode;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionService {
    TransactionResponse getByTransactionId(String transactionId);
    List<TransactionResponse> getByAccount(String accountNumber, LocalDateTime from, LocalDateTime to, PaymentMode mode);
    byte[] generateStatement(String accountNumber);
    TransactionSummaryResponse getSummary(String accountNumber);
}
