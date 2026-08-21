package com.paystream.frauddetectionservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.FraudAlertEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.frauddetectionservice.client.AiFraudClient;
import com.paystream.frauddetectionservice.client.dto.AiFraudResponse;
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
 * Builds the local transaction read-model and runs fraud detection against
 * every completed payment: the existing rule engine, plus ai-service's LLM+RAG
 * fraud analysis (previously implemented but never called by anything in the
 * platform). Either detector can independently trigger an alert; when both
 * fire, the alert reflects whichever is more severe. See FraudRuleEngineImpl
 * for the post-completion vs. pre-settlement blocking trade-off this implies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private static final String FRAUD_ALERT_TOPIC = "fraud.alert";

    private final TransactionLogRepository transactionLogRepository;
    private final FraudAlertRepository fraudAlertRepository;
    private final FraudRuleEngine fraudRuleEngine;
    private final AiFraudClient aiFraudClient;
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

            Optional<FraudRuleEngine.RuleEvaluationResult> ruleResult = fraudRuleEngine.evaluate(savedLog);
            AiFraudResponse aiResult = aiFraudClient.analyze(savedLog);
            boolean aiFlagged = isElevated(aiResult);

            if (ruleResult.isPresent() || aiFlagged) {
                raiseAlert(savedLog, ruleResult.orElse(null), aiFlagged ? aiResult : null);
            }
        } catch (Exception e) {
            log.error("Failed to process payment event for fraud check: {}", payload, e);
        }
    }

    private boolean isElevated(AiFraudResponse ai) {
        if (ai == null) {
            return false;
        }
        return "BLOCK".equalsIgnoreCase(ai.getRecommendation())
                || "REVIEW".equalsIgnoreCase(ai.getRecommendation())
                || "HIGH".equalsIgnoreCase(ai.getRiskLevel())
                || "CRITICAL".equalsIgnoreCase(ai.getRiskLevel());
    }

    private void raiseAlert(TransactionLog txn, FraudRuleEngine.RuleEvaluationResult ruleResult, AiFraudResponse aiResult) {
        int ruleScore = ruleResult != null ? ruleResult.riskScore() : 0;
        int aiScore = (aiResult != null && aiResult.getRiskScore() != null) ? Math.round(aiResult.getRiskScore()) : 0;
        RuleAction ruleAction = ruleResult != null ? RuleAction.valueOf(ruleResult.action()) : null;
        RuleAction aiAction = aiResult != null ? mapRecommendationToAction(aiResult.getRecommendation()) : null;

        FraudAlert alert = new FraudAlert();
        alert.setAccountNumber(txn.getAccountNumber());
        alert.setTransactionReference(txn.getPaymentReferenceNumber());
        alert.setRuleTriggered(ruleResult != null ? ruleResult.ruleTriggered() : "AI_FRAUD_DETECTION");
        alert.setRiskScore(Math.max(ruleScore, aiScore));
        alert.setAction(higherSeverity(ruleAction, aiAction));
        alert.setStatus(AlertStatus.OPEN);

        if (aiResult != null) {
            alert.setAiExplanation(aiResult.getExplanation());
            alert.setAiRiskScore(aiResult.getRiskScore());
            alert.setDetectionMethod(ruleResult != null ? "RULE_ENGINE + AI" : "AI");
        } else {
            alert.setDetectionMethod("RULE_ENGINE");
        }

        fraudAlertRepository.save(alert);

        FraudAlertEvent event = FraudAlertEvent.builder()
                .accountNumber(txn.getAccountNumber())
                .transactionReference(txn.getPaymentReferenceNumber())
                .ruleTriggered(alert.getRuleTriggered())
                .riskScore(alert.getRiskScore())
                .action(alert.getAction().name())
                .createdAt(Instant.now())
                .build();
        try {
            kafkaTemplate.send(FRAUD_ALERT_TOPIC, txn.getAccountNumber(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.error("Failed to publish FraudAlertEvent for {}", txn.getPaymentReferenceNumber(), e);
        }
    }

    private RuleAction mapRecommendationToAction(String recommendation) {
        if (recommendation == null) {
            return RuleAction.ALERT;
        }
        return switch (recommendation.toUpperCase()) {
            case "BLOCK" -> RuleAction.BLOCK;
            case "REVIEW" -> RuleAction.REVIEW;
            default -> RuleAction.ALERT;
        };
    }

    private RuleAction higherSeverity(RuleAction a, RuleAction b) {
        if (a == null) {
            return b != null ? b : RuleAction.ALERT;
        }
        if (b == null) {
            return a;
        }
        return severityRank(a) >= severityRank(b) ? a : b;
    }

    private int severityRank(RuleAction action) {
        return switch (action) {
            case BLOCK -> 2;
            case REVIEW -> 1;
            case ALERT -> 0;
        };
    }
}
