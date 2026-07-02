package com.paystream.rtgsservice.repository;

import com.paystream.rtgsservice.entity.RtgsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RtgsTransactionRepository extends JpaRepository<RtgsTransaction, UUID> {
    Optional<RtgsTransaction> findByRtgsReferenceNumber(String rtgsReferenceNumber);
    List<RtgsTransaction> findByCustomerIdOrderByInitiatedAtDesc(String customerId);
    boolean existsByRtgsReferenceNumber(String rtgsReferenceNumber);
}
