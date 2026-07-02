package com.paystream.notificationservice.service.impl;

import com.paystream.notificationservice.dto.NotificationResponse;
import com.paystream.notificationservice.entity.NotificationLog;
import com.paystream.notificationservice.enums.NotificationChannel;
import com.paystream.notificationservice.enums.NotificationStatus;
import com.paystream.notificationservice.repository.NotificationLogRepository;
import com.paystream.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationLogRepository notificationLogRepository;

    @Override
    @Transactional
    public void send(String customerId, String message, String referenceId, NotificationChannel channel) {
        NotificationLog log = new NotificationLog();
        log.setCustomerId(customerId);
        log.setChannel(channel);
        log.setMessage(message);
        log.setReferenceId(referenceId);
        log.setStatus(NotificationStatus.SENT);
        notificationLogRepository.save(log);
    }

    @Override
    public List<NotificationResponse> getHistory(String customerId) {
        return notificationLogRepository.findByCustomerIdOrderBySentAtDesc(customerId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private NotificationResponse toResponse(NotificationLog log) {
        return NotificationResponse.builder()
                .id(log.getId())
                .customerId(log.getCustomerId())
                .channel(log.getChannel())
                .message(log.getMessage())
                .status(log.getStatus())
                .sentAt(log.getSentAt())
                .referenceId(log.getReferenceId())
                .build();
    }
}
