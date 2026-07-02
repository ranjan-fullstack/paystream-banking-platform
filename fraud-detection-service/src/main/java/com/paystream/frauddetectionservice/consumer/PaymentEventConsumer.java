package com.paystream.frauddetectionservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.FraudAlertEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.frauddetectionservice.entity.FraudAlert;
import com.paystream.frauddetectionservice.entity.TransactionLog;
import com.paystream.frauddetectionservice.enums.AlertStatus;
import com.paystream.frauddetectionservice.enums.RuleAction;
import com.paystream.frauddetectionservice.repository.FraudAlertRepository;
import com.paystream.frauddetectionservice.repository.TransactionLogRepository;
import com.paystream.frauddetectionservice.service.FraudRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Builds the local transaction read-model and runs the fraud rule engine
 * against every completed payment. See FraudRuleEngineImpl for the
 * post-completion vs. pre-settlement blocking trade-off this implies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String FRAUD_ALERT_TOPIC = "fraud.alert";

    private final TransactionLogRepository transactionLogRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudRuleEngine fraudRuleEngine;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
            "payment.neft.completed",
            "payment.rtgs.settled",
            "payment.imps.completed",
            "payment.upi.completed"
    }, groupId = "${spring.kafka.consumer.group-id:fraud-detection-service-group}")
    @Transactional
    public void onPaymentCompleted(String payload) {
        try {
            TransactionCompletedEvent event = objectMapper.readValue(payload, TransactionCompletedEvent.class);
            if (transactionLogRepository.existsByPaymentReferenceNumber(event.getPaymentReferenceNumber())) {
                return;
            }

            TransactionLog txnLog = new TransactionLog();
            txnLog.setAccountNumber(event.getDebitAccountNumber());
            txnLog.setBeneficiaryAccountNumber(event.getCreditAccountNumber());
            txnLog.setAmount(event.getAmount());
            txnLog.setPaymentMode(event.getPaymentMode());
            txnLog.setPaymentReferenceNumber(event.getPaymentReferenceNumber());
            txnLog.setOccurredAt(LocalDateTime.now());
            final TransactionLog savedLog = transactionLogRepository.save(txnLog);

            Optional<FraudRuleEngine.RuleEvaluationResult> result = fraudRuleEngine.evaluate(savedLog);
            result.ifPresent(r -> raiseAlert(savedLog, r));
        } catch (Exception e) {
            log.error("Failed to process payment event for fraud check: {}", payload, e);
        }
    }

    private void raiseAlert(TransactionLog txn, FraudRuleEngine.RuleEvaluationResult result) {
        FraudAlert alert = new FraudAlert();
        alert.setAccountNumber(txn.getAccountNumber());
        alert.setTransactionReference(txn.getPaymentReferenceNumber());
        alert.setRuleTriggered(result.ruleTriggered());
        alert.setRiskScore(result.riskScore());
        alert.setAction(RuleAction.valueOf(result.action()));
        alert.setStatus(AlertStatus.OPEN);
        fraudAlertRepository.save(alert);

        FraudAlertEvent event = FraudAlertEvent.builder()
                .accountNumber(txn.getAccountNumber())
                .transactionReference(txn.getPaymentReferenceNumber())
                .ruleTriggered(result.ruleTriggered())
                .riskScore(result.riskScore())
                .action(result.action())
                .createdAt(Instant.now())
                .build();
        try {
            kafkaTemplate.send(FRAUD_ALERT_TOPIC, txn.getAccountNumber(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish FraudAlertEvent for {}", txn.getPaymentReferenceNumber(), e);
        }
    }
}
