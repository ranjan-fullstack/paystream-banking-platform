package com.paystream.neftservice.repository;

import com.paystream.neftservice.entity.NeftBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NeftBatchRepository extends JpaRepository<NeftBatch, UUID> {
    Optional<NeftBatch> findByBatchNumber(String batchNumber);
}
