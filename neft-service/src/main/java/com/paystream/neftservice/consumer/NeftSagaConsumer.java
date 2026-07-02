package com.paystream.neftservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.neftservice.entity.NeftTransaction;
import com.paystream.neftservice.entity.OutboxEvent;
import com.paystream.neftservice.entity.ProcessedEvent;
import com.paystream.neftservice.enums.NeftStatus;
import com.paystream.neftservice.repository.NeftTransactionRepository;
import com.paystream.neftservice.repository.OutboxEventRepository;
import com.paystream.neftservice.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class NeftSagaConsumer {

    private static final String PAYMENT_MODE = "NEFT";
    private static final String NEFT_COMPLETED_TOPIC = "payment.neft.completed";

    private final NeftTransactionRepository neftTransactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"payment.credited", "payment.debit.failed", "payment.reversed", "payment.reversal.failed"},
        groupId = "${spring.kafka.consumer.group-id:neft-service-group}"
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
            log.error("NeftSagaConsumer failed to process event from topic {}: {}", topic, payload, e);
        }
    }

    private void handleCredited(String payload) throws Exception {
        PaymentCreditedEvent event = objectMapper.readValue(payload, PaymentCreditedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "CREDITED")) return;

        neftTransactionRepository.findByNeftReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(NeftStatus.COMPLETED);
                txn.setCompletedAt(LocalDateTime.now());
                neftTransactionRepository.save(txn);

                writeCompletionOutbox(txn, event.getSenderAccountNumber(), event.getBeneficiaryAccountNumber());
                markProcessed(event.getPaymentReferenceNumber(), "CREDITED");
                log.info("NEFT {} completed via saga", txn.getNeftReferenceNumber());
            });
    }

    private void handleDebitFailed(String payload) throws Exception {
        PaymentDebitFailedEvent event = objectMapper.readValue(payload, PaymentDebitFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED")) return;

        neftTransactionRepository.findByNeftReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(NeftStatus.FAILED);
                txn.setFailureReason(event.getFailureReason());
                neftTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED");
                log.warn("NEFT {} failed at debit: {}", txn.getNeftReferenceNumber(), event.getFailureReason());
            });
    }

    private void handleReversed(String payload) throws Exception {
        PaymentReversedEvent event = objectMapper.readValue(payload, PaymentReversedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSED")) return;

        neftTransactionRepository.findByNeftReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(NeftStatus.FAILED);
                txn.setFailureReason("Credit failed — debit reversed");
                neftTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSED");
                log.warn("NEFT {} failed — debit reversed successfully", txn.getNeftReferenceNumber());
            });
    }

    private void handleReversalFailed(String payload) throws Exception {
        PaymentReversalFailedEvent event = objectMapper.readValue(payload, PaymentReversalFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED")) return;

        neftTransactionRepository.findByNeftReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(NeftStatus.RECONCILIATION_REQUIRED);
                txn.setFailureReason("Credit failed and reversal failed — manual reconciliation needed");
                neftTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED");
                log.error("NEFT {} requires manual reconciliation", txn.getNeftReferenceNumber());
            });
    }

    private void writeCompletionOutbox(NeftTransaction txn, String debitAccount, String creditAccount) {
        try {
            TransactionCompletedEvent completedEvent = TransactionCompletedEvent.builder()
                    .transactionId(txn.getId().toString())
                    .paymentMode(PAYMENT_MODE)
                    .paymentReferenceNumber(txn.getNeftReferenceNumber())
                    .debitAccountNumber(debitAccount)
                    .creditAccountNumber(creditAccount)
                    .amount(txn.getAmount())
                    .status(txn.getStatus().name())
                    .completedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("NeftTransaction");
            outboxEvent.setAggregateId(txn.getNeftReferenceNumber());
            outboxEvent.setEventType("TransactionCompleted");
            outboxEvent.setTopic(NEFT_COMPLETED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(completedEvent));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write completion outbox for NEFT {}", txn.getNeftReferenceNumber(), e);
        }
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
