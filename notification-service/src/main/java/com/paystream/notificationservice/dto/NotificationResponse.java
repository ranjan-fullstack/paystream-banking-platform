package com.paystream.notificationservice.dto;

import com.paystream.notificationservice.enums.NotificationChannel;
import com.paystream.notificationservice.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String customerId;
    private NotificationChannel channel;
    private String message;
    private NotificationStatus status;
    private LocalDateTime sentAt;
    private String referenceId;
}
