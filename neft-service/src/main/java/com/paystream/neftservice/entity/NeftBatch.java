package com.paystream.neftservice.entity;

import com.paystream.neftservice.enums.NeftBatchStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "neft_batches")
@Getter
@Setter
public class NeftBatch {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String batchNumber; // BATCH-YYYYMMDD-HHMM

    @Column(nullable = false)
    private LocalDateTime scheduledAt;

    private LocalDateTime processedAt;

    @Column(nullable = false)
    private Integer totalTransactions = 0;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer successCount = 0;

    @Column(nullable = false)
    private Integer failureCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NeftBatchStatus status = NeftBatchStatus.SCHEDULED;
}
