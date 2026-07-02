package com.paystream.impsservice.repository;

import com.paystream.impsservice.entity.ImpsTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImpsTransactionRepository extends JpaRepository<ImpsTransaction, UUID> {
    Optional<ImpsTransaction> findByImpsReferenceNumber(String impsReferenceNumber);
    List<ImpsTransaction> findByCustomerIdOrderByInitiatedAtDesc(String customerId);
    boolean existsByImpsReferenceNumber(String impsReferenceNumber);
}
