package com.paystream.customerservice.repository;

import com.paystream.customerservice.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {
    List<KycDocument> findByCustomerId(UUID customerId);
}
