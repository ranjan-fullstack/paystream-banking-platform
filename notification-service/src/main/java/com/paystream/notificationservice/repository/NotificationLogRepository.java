package com.paystream.notificationservice.repository;

import com.paystream.notificationservice.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {
    List<NotificationLog> findByCustomerIdOrderBySentAtDesc(String customerId);
}
