package com.paystream.impsservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.impsservice.entity.ImpsTransaction;
import com.paystream.impsservice.entity.OutboxEvent;
import com.paystream.impsservice.entity.ProcessedEvent;
import com.paystream.impsservice.enums.ImpsStatus;
import com.paystream.impsservice.repository.ImpsTransactionRepository;
import com.paystream.impsservice.repository.OutboxEventRepository;
import com.paystream.impsservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImpsSagaConsumer {

    private static final String PAYMENT_MODE = "IMPS";
    private static final String IMPS_COMPLETED_TOPIC = "payment.imps.completed";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ImpsTransactionRepository impsTransactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"payment.credited", "payment.debit.failed", "payment.reversed", "payment.reversal.failed"},
        groupId = "${spring.kafka.consumer.group-id:imps-service-group}"
    )
    @Transactional
    public void onSagaEvent(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            switch (topic) {
                case "payment.credited"        -> handleCredited(payload);
                case "payment.debit.failed"    -> handleDebitFailed(payload);
                case "payment.reversed"        -> handleReversed(payload);
                case "payment.reversal.failed" -> handleReversalFailed(payload);
            }
        } catch (Exception e) {
            log.error("ImpsSagaConsumer failed to process event from topic {}: {}", topic, payload, e);
        }
    }

    private void handleCredited(String payload) throws Exception {
        PaymentCreditedEvent event = objectMapper.readValue(payload, PaymentCreditedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "CREDITED")) return;

        impsTransactionRepository.findByImpsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(ImpsStatus.COMPLETED);
                txn.setRrn(generateRrn());
                txn.setCompletedAt(LocalDateTime.now());
                impsTransactionRepository.save(txn);

                writeCompletionOutbox(txn, event.getSenderAccountNumber(), event.getBeneficiaryAccountNumber());
                markProcessed(event.getPaymentReferenceNumber(), "CREDITED");
                log.info("IMPS {} completed via saga", txn.getImpsReferenceNumber());
            });
    }

    private void handleDebitFailed(String payload) throws Exception {
        PaymentDebitFailedEvent event = objectMapper.readValue(payload, PaymentDebitFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED")) return;

        impsTransactionRepository.findByImpsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(ImpsStatus.FAILED);
                txn.setFailureReason(event.getFailureReason());
                impsTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED");
                log.warn("IMPS {} failed at debit: {}", txn.getImpsReferenceNumber(), event.getFailureReason());
            });
    }

    private void handleReversed(String payload) throws Exception {
        PaymentReversedEvent event = objectMapper.readValue(payload, PaymentReversedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSED")) return;

        impsTransactionRepository.findByImpsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(ImpsStatus.FAILED);
                txn.setFailureReason("Credit failed — debit reversed");
                impsTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSED");
                log.warn("IMPS {} failed — debit reversed successfully", txn.getImpsReferenceNumber());
            });
    }

    private void handleReversalFailed(String payload) throws Exception {
        PaymentReversalFailedEvent event = objectMapper.readValue(payload, PaymentReversalFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED")) return;

        impsTransactionRepository.findByImpsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(ImpsStatus.RECONCILIATION_REQUIRED);
                txn.setFailureReason("Credit failed and reversal failed — manual reconciliation needed");
                impsTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED");
                log.error("IMPS {} requires manual reconciliation", txn.getImpsReferenceNumber());
            });
    }

    private void writeCompletionOutbox(ImpsTransaction txn, String debitAccount, String creditAccount) {
        try {
            TransactionCompletedEvent completedEvent = TransactionCompletedEvent.builder()
                    .transactionId(txn.getId().toString())
                    .paymentMode(PAYMENT_MODE)
                    .paymentReferenceNumber(txn.getImpsReferenceNumber())
                    .debitAccountNumber(debitAccount)
                    .creditAccountNumber(creditAccount)
                    .amount(txn.getAmount())
                    .status(txn.getStatus().name())
                    .completedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("ImpsTransaction");
            outboxEvent.setAggregateId(txn.getImpsReferenceNumber());
            outboxEvent.setEventType("TransactionCompleted");
            outboxEvent.setTopic(IMPS_COMPLETED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(completedEvent));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write completion outbox for IMPS {}", txn.getImpsReferenceNumber(), e);
        }
    }

    private String generateRrn() {
        return String.format("%012d", Math.abs(RANDOM.nextLong() % 1_000_000_000_000L));
    }

    private boolean isAlreadyProcessed(String referenceNumber, String eventType) {
        return processedEventRepository.existsByPaymentReferenceNumberAndEventType(referenceNumber, eventType);
    }

    private void markProcessed(String referenceNumber, String eventType) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setPaymentReferenceNumber(referenceNumber);
        pe.setEventType(eventType);
        processedEventRepository.save(pe);
    }
}
