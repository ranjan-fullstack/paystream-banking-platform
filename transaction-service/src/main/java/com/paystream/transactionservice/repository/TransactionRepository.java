package com.paystream.transactionservice.repository;

import com.paystream.transactionservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, java.util.UUID>, JpaSpecificationExecutor<Transaction> {
    Optional<Transaction> findByTransactionId(String transactionId);
    boolean existsByTransactionId(String transactionId);
    boolean existsByPaymentReferenceNumber(String paymentReferenceNumber);
}
