package com.paystream.rtgsservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "processed_events",
    uniqueConstraints = @UniqueConstraint(columnNames = {"payment_reference_number", "event_type"})
)
@Getter
@Setter
public class ProcessedEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "payment_reference_number", nullable = false)
    private String paymentReferenceNumber;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
