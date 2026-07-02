package com.paystream.upiservice.repository;

import com.paystream.upiservice.entity.UpiTransaction;
import com.paystream.upiservice.enums.UpiTransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UpiTransactionRepository extends JpaRepository<UpiTransaction, UUID> {
    Optional<UpiTransaction> findByUpiTransactionId(String upiTransactionId);
    List<UpiTransaction> findBySenderVpaOrReceiverVpaOrderByInitiatedAtDesc(String senderVpa, String receiverVpa);
    boolean existsByUpiTransactionId(String upiTransactionId);
    List<UpiTransaction> findByStatusAndExpiresAtBefore(UpiTransactionStatus status, LocalDateTime now);
}
