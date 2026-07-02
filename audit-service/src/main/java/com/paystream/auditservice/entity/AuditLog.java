package com.paystream.auditservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Append-only audit trail. No update/delete operations are exposed anywhere
 * in this service - rows are written once by AuditEventConsumer and only
 * ever read back via AuditController.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String eventType; // e.g. NEFT_COMPLETED, FRAUD_ALERT, ACCOUNT_CREATED

    @Column(nullable = false)
    private String entityType; // ACCOUNT / TRANSACTION / FRAUD_ALERT etc.

    private String entityId;

    private String performedBy;

    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String oldValue;

    @Column(columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false, updatable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
