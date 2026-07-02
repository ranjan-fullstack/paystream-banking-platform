package com.paystream.upiservice.repository;

import com.paystream.upiservice.entity.UpiCollectRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UpiCollectRequestRepository extends JpaRepository<UpiCollectRequest, UUID> {
    Optional<UpiCollectRequest> findByUpiTransactionId(UUID upiTransactionId);
}
