package com.paystream.notificationservice.service;

import com.paystream.notificationservice.dto.NotificationResponse;
import com.paystream.notificationservice.entity.NotificationLog;
import com.paystream.notificationservice.enums.NotificationChannel;
import com.paystream.notificationservice.enums.NotificationStatus;
import com.paystream.notificationservice.repository.NotificationLogRepository;
import com.paystream.notificationservice.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService unit tests")
class NotificationServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @Test
    @DisplayName("Should persist a SENT notification log when sending a notification")
    void testSend_Success() {
        // Given
        when(notificationLogRepository.save(any(NotificationLog.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        notificationService.send("1111222233334444", "Your NEFT of Rs.25000 is successful", "NEFT202606290001", NotificationChannel.SMS);

        // Then
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository).save(captor.capture());
        assertThat(captor.getValue().getCustomerId()).isEqualTo("1111222233334444");
        assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.SMS);
        assertThat(captor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(captor.getValue().getReferenceId()).isEqualTo("NEFT202606290001");
    }

    @Test
    @DisplayName("Should return the customer's notification history ordered by most recent")
    void testGetCustomerNotifications_Success() {
        // Given
        NotificationLog log1 = new NotificationLog();
        log1.setId(1L);
        log1.setCustomerId("CIF001234");
        log1.setChannel(NotificationChannel.SMS);
        log1.setMessage("Your NEFT of Rs.25000 is successful");
        log1.setStatus(NotificationStatus.SENT);
        log1.setSentAt(LocalDateTime.now());

        NotificationLog log2 = new NotificationLog();
        log2.setId(2L);
        log2.setCustomerId("CIF001234");
        log2.setChannel(NotificationChannel.EMAIL);
        log2.setMessage("Your NEFT of Rs.25000 is successful");
        log2.setStatus(NotificationStatus.SENT);
        log2.setSentAt(LocalDateTime.now());

        when(notificationLogRepository.findByCustomerIdOrderBySentAtDesc("CIF001234"))
                .thenReturn(List.of(log2, log1));

        // When
        List<NotificationResponse> history = notificationService.getHistory("CIF001234");

        // Then
        assertThat(history).hasSize(2);
        assertThat(history).extracting(NotificationResponse::getChannel)
                .containsExactly(NotificationChannel.EMAIL, NotificationChannel.SMS);
    }

    @Test
    @DisplayName("Should return an empty list when the customer has no notifications")
    void testGetCustomerNotifications_Empty() {
        // Given
        when(notificationLogRepository.findByCustomerIdOrderBySentAtDesc("CIF999999")).thenReturn(List.of());

        // When
        List<NotificationResponse> history = notificationService.getHistory("CIF999999");

        // Then
        assertThat(history).isEmpty();
    }
}
