package com.paystream.impsservice.repository;

import com.paystream.impsservice.entity.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    boolean existsByPaymentReferenceNumberAndEventType(String paymentReferenceNumber, String eventType);
}
