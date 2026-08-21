package com.paystream.frauddetectionservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.frauddetectionservice.client.AiFraudClient;
import com.paystream.frauddetectionservice.entity.FraudAlert;
import com.paystream.frauddetectionservice.entity.TransactionLog;
import com.paystream.frauddetectionservice.repository.FraudAlertRepository;
import com.paystream.frauddetectionservice.repository.TransactionLogRepository;
import com.paystream.frauddetectionservice.service.FraudRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventConsumer unit tests")
class PaymentEventConsumerTest {

    @Mock
    private TransactionLogRepository transactionLogRepository;

    @Mock
    private FraudAlertRepository fraudAlertRepository;

    @Mock
    private FraudRuleEngine fraudRuleEngine;

    @Mock
    private AiFraudClient aiFraudClient;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private PaymentEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new PaymentEventConsumer(transactionLogRepository, fraudAlertRepository, fraudRuleEngine, aiFraudClient, kafkaTemplate, objectMapper);
    }

    private String paymentEventJson(String referenceNumber) throws Exception {
        TransactionCompletedEvent event = TransactionCompletedEvent.builder()
                .transactionId("TXN-" + referenceNumber)
                .paymentMode("IMPS")
                .paymentReferenceNumber(referenceNumber)
                .debitAccountNumber("1111222233334444")
                .creditAccountNumber("9999888877776666")
                .amount(new BigDecimal("5000"))
                .status("COMPLETED")
                .completedAt(Instant.now())
                .build();
        return objectMapper.writeValueAsString(event);
    }

    @Test
    @DisplayName("Should log the transaction and raise no alert when no fraud rule is triggered")
    void testOnPaymentCompleted_noRuleTriggered_noAlert() throws Exception {
        // Given
        String payload = paymentEventJson("IMPS202606290001");
        when(transactionLogRepository.existsByPaymentReferenceNumber("IMPS202606290001")).thenReturn(false);
        when(transactionLogRepository.save(any(TransactionLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fraudRuleEngine.evaluate(any(TransactionLog.class))).thenReturn(Optional.empty());

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        verify(transactionLogRepository).save(any(TransactionLog.class));
        verify(fraudAlertRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Should raise and publish a fraud alert when a rule is triggered")
    void testOnPaymentCompleted_ruleTriggered_raisesAlertAndPublishes() throws Exception {
        // Given
        String payload = paymentEventJson("IMPS202606290002");
        when(transactionLogRepository.existsByPaymentReferenceNumber("IMPS202606290002")).thenReturn(false);
        when(transactionLogRepository.save(any(TransactionLog.class))).thenAnswer(inv -> inv.getArgument(0));
        when(fraudRuleEngine.evaluate(any(TransactionLog.class)))
                .thenReturn(Optional.of(new FraudRuleEngine.RuleEvaluationResult("VELOCITY_CHECK", 60, "ALERT")));

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        ArgumentCaptor<FraudAlert> captor = ArgumentCaptor.forClass(FraudAlert.class);
        verify(fraudAlertRepository).save(captor.capture());
        assertThat(captor.getValue().getRuleTriggered()).isEqualTo("VELOCITY_CHECK");
        assertThat(captor.getValue().getRiskScore()).isEqualTo(60);
        verify(kafkaTemplate).send(eq("fraud.alert"), eq("1111222233334444"), any());
    }

    @Test
    @DisplayName("Should skip processing a payment event whose reference was already logged")
    void testOnPaymentCompleted_duplicateReference_skipsProcessing() throws Exception {
        // Given
        String payload = paymentEventJson("IMPS202606290003");
        when(transactionLogRepository.existsByPaymentReferenceNumber("IMPS202606290003")).thenReturn(true);

        // When
        consumer.onPaymentCompleted(payload);

        // Then
        verify(transactionLogRepository, never()).save(any());
        verify(fraudRuleEngine, never()).evaluate(any());
    }
}
