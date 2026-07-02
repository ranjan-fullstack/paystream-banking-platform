package com.paystream.neftservice.repository;

import com.paystream.neftservice.entity.NeftTransaction;
import com.paystream.neftservice.enums.NeftStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NeftTransactionRepository extends JpaRepository<NeftTransaction, UUID> {
    Optional<NeftTransaction> findByNeftReferenceNumber(String neftReferenceNumber);
    List<NeftTransaction> findByCustomerIdOrderByInitiatedAtDesc(String customerId);
    List<NeftTransaction> findByStatus(NeftStatus status);
    boolean existsByNeftReferenceNumber(String neftReferenceNumber);
}
