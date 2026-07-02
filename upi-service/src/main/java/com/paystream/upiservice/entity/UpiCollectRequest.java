package com.paystream.upiservice.entity;

import com.paystream.upiservice.enums.CollectRequestStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "upi_collect_requests")
@Getter
@Setter
public class UpiCollectRequest {

    @Id
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "upi_transaction_id", nullable = false)
    private UpiTransaction upiTransaction;

    @Column(nullable = false)
    private String requestedBy; // VPA of the payee requesting money

    @Column(nullable = false)
    private String requestedFrom; // VPA of the payer

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectRequestStatus status = CollectRequestStatus.PENDING;
}
