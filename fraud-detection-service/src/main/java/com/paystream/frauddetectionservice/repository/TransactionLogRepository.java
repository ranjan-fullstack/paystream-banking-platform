package com.paystream.frauddetectionservice.repository;

import com.paystream.frauddetectionservice.entity.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, java.util.UUID> {

    List<TransactionLog> findByAccountNumberAndOccurredAtAfter(String accountNumber, LocalDateTime since);

    List<TransactionLog> findByAccountNumberAndBeneficiaryAccountNumberAndAmountAndOccurredAtAfter(
            String accountNumber, String beneficiaryAccountNumber, BigDecimal amount, LocalDateTime since);

    boolean existsByPaymentReferenceNumber(String paymentReferenceNumber);
}
