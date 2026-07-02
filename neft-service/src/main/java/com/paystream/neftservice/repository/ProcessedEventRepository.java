package com.paystream.neftservice.repository;

import com.paystream.neftservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByPaymentReferenceNumberAndEventType(String paymentReferenceNumber, String eventType);
}
