package com.paystream.impsservice.entity;

import com.paystream.impsservice.enums.ImpsStatus;
import com.paystream.impsservice.enums.TransferMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "imps_transactions")
@Getter
@Setter
public class ImpsTransaction {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(unique = true, nullable = false, updatable = false)
    private String impsReferenceNumber;

    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferMode transferMode;

    @Column(nullable = false)
    private String senderAccountNumber;

    private String senderMobile;

    private String beneficiaryAccountNumber;
    private String beneficiaryIfsc;
    private String beneficiaryMobile;
    private String beneficiaryMmid;

    @Column(nullable = false)
    private String beneficiaryName;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImpsStatus status = ImpsStatus.INITIATED;

    @Column(nullable = false, updatable = false)
    private LocalDateTime initiatedAt;

    private LocalDateTime completedAt;

    private String failureReason;

    private String rrn; // Retrieval Reference Number

    @PrePersist
    protected void onCreate() {
        initiatedAt = LocalDateTime.now();
    }
}
