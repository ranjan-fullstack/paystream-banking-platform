package com.paystream.notificationservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.AccountCreatedEvent;
import com.paystream.commonlib.event.FraudAlertEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.notificationservice.enums.NotificationChannel;
import com.paystream.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment.neft.completed", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void onNeftCompleted(String payload) {
        handlePaymentEvent(payload, "Your NEFT of Rs.%s to account %s is successful. Ref: %s");
    }

    @KafkaListener(topics = "payment.rtgs.settled", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void onRtgsSettled(String payload) {
        handlePaymentEvent(payload, "Your RTGS transfer of Rs.%s to account %s has settled. Ref: %s");
    }

    @KafkaListener(topics = "payment.imps.completed", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void onImpsCompleted(String payload) {
        handlePaymentEvent(payload, "Your IMPS transfer of Rs.%s to account %s is complete. Ref: %s");
    }

    @KafkaListener(topics = "payment.upi.completed", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void onUpiCompleted(String payload) {
        handlePaymentEvent(payload, "Your UPI payment of Rs.%s to %s is successful. UPI Ref: %s");
    }

    @KafkaListener(topics = "fraud.alert", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void onFraudAlert(String payload) {
        try {
            FraudAlertEvent event = objectMapper.readValue(payload, FraudAlertEvent.class);
            String message = "ALERT: Suspicious activity detected on your account (" + event.getRuleTriggered() + ")";
            notify(event.getAccountNumber(), message, event.getTransactionReference());
        } catch (Exception e) {
            log.error("Failed to process fraud alert event: {}", payload, e);
        }
    }

    @KafkaListener(topics = "account.created", groupId = "${spring.kafka.consumer.group-id:notification-service-group}")
    public void onAccountCreated(String payload) {
        try {
            AccountCreatedEvent event = objectMapper.readValue(payload, AccountCreatedEvent.class);
            String message = "Welcome to PayStream! Your account " + event.getAccountNumber() + " is ready.";
            notify(event.getCustomerId(), message, event.getAccountNumber());
        } catch (Exception e) {
            log.error("Failed to process account created event: {}", payload, e);
        }
    }

    private void handlePaymentEvent(String payload, String messageTemplate) {
        try {
            TransactionCompletedEvent event = objectMapper.readValue(payload, TransactionCompletedEvent.class);
            String message = String.format(messageTemplate,
                    event.getAmount(), event.getCreditAccountNumber(), event.getPaymentReferenceNumber());
            notify(event.getDebitAccountNumber(), message, event.getPaymentReferenceNumber());
        } catch (Exception e) {
            log.error("Failed to process payment completion event: {}", payload, e);
        }
    }

    private void notify(String recipientId, String message, String referenceId) {
        notificationService.send(recipientId, message, referenceId, NotificationChannel.SMS);
        notificationService.send(recipientId, message, referenceId, NotificationChannel.EMAIL);
    }
}
