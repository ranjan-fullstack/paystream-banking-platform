package com.paystream.frauddetectionservice.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Local read-model of completed payments, built from the same Kafka events
 * this service consumes. Lets the rule engine evaluate velocity/anomaly
 * checks without a synchronous call back into transaction-service.
 */
@Entity
@Table(name = "transaction_logs")
@Getter
@Setter
public class TransactionLog {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String accountNumber; // the debit (sending) account

    @Column(nullable = false)
    private String beneficiaryAccountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String paymentMode;

    @Column(nullable = false, unique = true)
    private String paymentReferenceNumber;

    @Column(nullable = false)
    private LocalDateTime occurredAt;
}
