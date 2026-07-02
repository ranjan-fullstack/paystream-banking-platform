package com.paystream.auditservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.auditservice.service.AuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditEventConsumer unit tests")
class AuditEventConsumerTest {

    @Mock
    private AuditService auditService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuditEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AuditEventConsumer(auditService, objectMapper);
    }

    @Test
    @DisplayName("Should record an audit entry for a NEFT payment completion event")
    void testOnEvent_PaymentTopic_recordsAuditLog() {
        // Given
        String payload = "{\"paymentReferenceNumber\":\"NEFT202606290001\",\"amount\":25000}";

        // When
        consumer.onEvent(payload, "payment.neft.completed");

        // Then
        verify(auditService).record(eq("PAYMENT_NEFT_COMPLETED"), eq("TRANSACTION"), eq("NEFT202606290001"), isNull(), eq(payload));
    }

    @Test
    @DisplayName("Should record an audit entry for a fraud alert event")
    void testOnEvent_FraudAlertTopic_recordsAuditLog() {
        // Given
        String payload = "{\"transactionReference\":\"IMPS202606290002\",\"ruleTriggered\":\"VELOCITY_CHECK\"}";

        // When
        consumer.onEvent(payload, "fraud.alert");

        // Then
        verify(auditService).record(eq("FRAUD_ALERT"), eq("FRAUD_ALERT"), eq("IMPS202606290002"), isNull(), eq(payload));
    }

    @Test
    @DisplayName("Should record an audit entry for an account created event")
    void testOnEvent_AccountCreatedTopic_recordsAuditLog() {
        // Given
        String payload = "{\"accountNumber\":\"1111222233334444\",\"customerId\":\"CIF001234\"}";

        // When
        consumer.onEvent(payload, "account.created");

        // Then
        verify(auditService).record(eq("ACCOUNT_CREATED"), eq("ACCOUNT"), eq("1111222233334444"), isNull(), eq(payload));
    }

    @Test
    @DisplayName("Should not throw and should skip recording when the payload is malformed JSON")
    void testOnEvent_MalformedPayload_doesNotThrow() {
        // Given
        String payload = "not-valid-json";

        // When
        consumer.onEvent(payload, "payment.neft.completed");

        // Then
        verify(auditService, never()).record(eq("PAYMENT_NEFT_COMPLETED"), eq("TRANSACTION"), org.mockito.ArgumentMatchers.any(), isNull(), eq(payload));
    }
}
