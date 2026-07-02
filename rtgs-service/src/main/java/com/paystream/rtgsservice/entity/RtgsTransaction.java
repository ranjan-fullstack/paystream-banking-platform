package com.paystream.rtgsservice.entity;

import com.paystream.rtgsservice.enums.RtgsPurpose;
import com.paystream.rtgsservice.enums.RtgsStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "rtgs_transactions")
@Getter
@Setter
public class RtgsTransaction {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String rtgsReferenceNumber; // RTGS + YYYYMMDD + sequence

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String senderAccountNumber;

    @Column(nullable = false)
    private String senderIfsc;

    @Column(nullable = false)
    private String beneficiaryAccountNumber;

    @Column(nullable = false)
    private String beneficiaryIfsc;

    @Column(nullable = false)
    private String beneficiaryName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RtgsPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RtgsStatus status = RtgsStatus.INITIATED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    private LocalDateTime settledAt;

    private String failureReason;

    private String rbiUtrNumber;

    @PrePersist
    protected void onCreate() {
        initiatedAt = LocalDateTime.now();
    }
}
