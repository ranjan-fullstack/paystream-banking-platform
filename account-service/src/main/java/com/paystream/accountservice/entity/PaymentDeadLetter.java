package com.paystream.accountservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_dead_letters")
@Getter
@Setter
public class PaymentDeadLetter {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String originalTopic;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Column(nullable = false)
    private LocalDateTime failedAt;

    @Column(nullable = false)
    private String status = "PENDING_REVIEW";

    @PrePersist
    protected void onCreate() {
        if (failedAt == null) {
            failedAt = LocalDateTime.now();
        }
    }
}
