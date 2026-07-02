package com.paystream.notificationservice.entity;

import com.paystream.notificationservice.enums.NotificationChannel;
import com.paystream.notificationservice.enums.NotificationStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_logs")
@Getter
@Setter
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Recipient identifier. For account-created events this is the real
     * customerId; for payment-completion/fraud events (where the upstream
     * event only carries account numbers, not customerId) this holds the
     * account number instead, since notification-service has no synchronous
     * lookup back into customer-service/account-service in this build.
     */
    @Column(nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status = NotificationStatus.SENT;

    @Column(nullable = false)
    private LocalDateTime sentAt;

    private String referenceId;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
