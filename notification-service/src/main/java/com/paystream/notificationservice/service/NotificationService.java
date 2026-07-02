package com.paystream.notificationservice.service;

import com.paystream.notificationservice.dto.NotificationResponse;
import com.paystream.notificationservice.enums.NotificationChannel;

import java.util.List;

public interface NotificationService {
    void send(String customerId, String message, String referenceId, NotificationChannel channel);
    List<NotificationResponse> getHistory(String customerId);
}
