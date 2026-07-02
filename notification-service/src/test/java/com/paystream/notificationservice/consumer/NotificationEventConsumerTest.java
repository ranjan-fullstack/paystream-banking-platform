package com.paystream.notificationservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.FraudAlertEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.notificationservice.enums.NotificationChannel;
import com.paystream.notificationservice.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationEventConsumer unit tests")
class NotificationEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private NotificationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationEventConsumer(notificationService, objectMapper);
    }

    private String paymentEventJson(String paymentMode, String referenceNumber, String debitAcc, String creditAcc, BigDecimal amount) throws Exception {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId("TXN-" + referenceNumber)
                .paymentMode(paymentMode)
                .paymentReferenceNumber(referenceNumber)
                .debitAccountNumber(debitAcc)
                .creditAccountNumber(creditAcc)
                .amount(amount)
                .status("COMPLETED")
                .completedAt(Instant.now())
                .build();
        return objectMapper.writeValueAsString(event);
    }

    @Test
    @DisplayName("Should send SMS and email notifications when a NEFT completion event is consumed")
    void testSendNeftNotification_Success() throws Exception {
        // Given
        String payload = paymentEventJson("NEFT", "NEFT202606290001", "1111222233334444", "5555666677778888", new BigDecimal("25000"));

        // When
        consumer.onNeftCompleted(payload);

        // Then
        verify(notificationService).send(eq("1111222233334444"), contains("NEFT"), eq("NEFT202606290001"), eq(NotificationChannel.SMS));
        verify(notificationService).send(eq("1111222233334444"), contains("NEFT"), eq("NEFT202606290001"), eq(NotificationChannel.EMAIL));
    }

    @Test
    @DisplayName("Should send SMS and email notifications when an RTGS settlement event is consumed")
    void testSendRtgsNotification_Success() throws Exception {
        // Given
        String payload = paymentEventJson("RTGS", "RTGS202606290002", "1111222233334444", "5555666677778888", new BigDecimal("500000"));

        // When
        consumer.onRtgsSettled(payload);

        // Then
        verify(notificationService).send(eq("1111222233334444"), contains("RTGS"), eq("RTGS202606290002"), eq(NotificationChannel.SMS));
        verify(notificationService).send(eq("1111222233334444"), contains("RTGS"), eq("RTGS202606290002"), eq(NotificationChannel.EMAIL));
    }

    @Test
    @DisplayName("Should send SMS and email notifications when an IMPS completion event is consumed")
    void testSendImpsNotification_Success() throws Exception {
        // Given
        String payload = paymentEventJson("IMPS", "IMPS202606290003", "1111222233334444", "5555666677778888", new BigDecimal("7500"));

        // When
        consumer.onImpsCompleted(payload);

        // Then
        verify(notificationService).send(eq("1111222233334444"), contains("IMPS"), eq("IMPS202606290003"), eq(NotificationChannel.SMS));
        verify(notificationService).send(eq("1111222233334444"), contains("IMPS"), eq("IMPS202606290003"), eq(NotificationChannel.EMAIL));
    }

    @Test
    @DisplayName("Should send SMS and email notifications when a UPI completion event is consumed")
    void testSendUpiNotification_Success() throws Exception {
        // Given
        String payload = paymentEventJson("UPI", "UPI202606290004", "1111222233334444", "9999888877776666", new BigDecimal("899"));

        // When
        consumer.onUpiCompleted(payload);

        // Then
        verify(notificationService).send(eq("1111222233334444"), contains("UPI"), eq("UPI202606290004"), eq(NotificationChannel.SMS));
        verify(notificationService).send(eq("1111222233334444"), contains("UPI"), eq("UPI202606290004"), eq(NotificationChannel.EMAIL));
    }

    @Test
    @DisplayName("Should send SMS and email notifications when a fraud alert event is consumed")
    void testSendFraudAlertNotification_Success() throws Exception {
        // Given
        FraudAlertEvent event = FraudAlertEvent.builder()
                .accountNumber("1111222233334444")
                .transactionReference("IMPS202606290005")
                .ruleTriggered("VELOCITY_CHECK")
                .riskScore(60)
                .action("ALERT")
                .createdAt(Instant.now())
                .build();
        String payload = objectMapper.writeValueAsString(event);

        // When
        consumer.onFraudAlert(payload);

        // Then
        verify(notificationService, times(1)).send(eq("1111222233334444"), contains("VELOCITY_CHECK"), eq("IMPS202606290005"), eq(NotificationChannel.SMS));
        verify(notificationService, times(1)).send(eq("1111222233334444"), contains("VELOCITY_CHECK"), eq("IMPS202606290005"), eq(NotificationChannel.EMAIL));
    }
}
