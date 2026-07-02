package com.paystream.auditservice.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.auditservice.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Tap-and-record audit listener. performedBy is left unset for these events
 * since payment/fraud/account topics carry account or reference numbers, not
 * the acting userId - there is no login/admin-action event feed wired into
 * Kafka yet, so GET /api/v1/audit/user/{userId} will only ever return entries
 * once such a feed exists upstream.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditEventConsumer {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
            "payment.neft.completed",
            "payment.rtgs.settled",
            "payment.imps.completed",
            "payment.upi.completed",
            "payment.reconciliation.required",
            "payment.reversal.failed",
            "fraud.alert",
            "account.created"
    }, groupId = "${spring.kafka.consumer.group-id:audit-service-group}")
    public void onEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String entityType = entityTypeFor(topic);
            String entityId = extractEntityId(node);
            String eventType = topic.toUpperCase().replace('.', '_');

            auditService.record(eventType, entityType, entityId, null, payload);
        } catch (Exception e) {
            log.error("Failed to record audit entry for topic {}: {}", topic, payload, e);
        }
    }

    private String entityTypeFor(String topic) {
        if (topic.startsWith("payment.")) return "TRANSACTION";
        if (topic.equals("fraud.alert")) return "FRAUD_ALERT";
        if (topic.equals("account.created")) return "ACCOUNT";
        return "UNKNOWN";
    }

    private String extractEntityId(JsonNode node) {
        for (String field : new String[]{"paymentReferenceNumber", "transactionReference", "accountNumber", "transactionId"}) {
            if (node.hasNonNull(field)) {
                return node.get(field).asText();
            }
        }
        return null;
    }
}
