package com.paystream.upiservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.upiservice.entity.OutboxEvent;
import com.paystream.upiservice.entity.ProcessedEvent;
import com.paystream.upiservice.entity.UpiTransaction;
import com.paystream.upiservice.enums.UpiTransactionStatus;
import com.paystream.upiservice.repository.OutboxEventRepository;
import com.paystream.upiservice.repository.ProcessedEventRepository;
import com.paystream.upiservice.repository.UpiTransactionRepository;
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
public class UpiSagaConsumer {

    private static final String PAYMENT_MODE = "UPI";
    private static final String UPI_COMPLETED_TOPIC = "payment.upi.completed";

    private final UpiTransactionRepository upiTransactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"payment.credited", "payment.debit.failed", "payment.reversed", "payment.reversal.failed"},
        groupId = "${spring.kafka.consumer.group-id:upi-service-group}"
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
            log.error("UpiSagaConsumer failed to process event from topic {}: {}", topic, payload, e);
        }
    }

    private void handleCredited(String payload) throws Exception {
        PaymentCreditedEvent event = objectMapper.readValue(payload, PaymentCreditedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "CREDITED")) return;

        upiTransactionRepository.findByUpiTransactionId(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(UpiTransactionStatus.COMPLETED);
                txn.setNpciTransactionId("NPCI" + Instant.now().toEpochMilli());
                txn.setCompletedAt(LocalDateTime.now());
                upiTransactionRepository.save(txn);

                writeCompletionOutbox(txn, event.getSenderAccountNumber(), event.getBeneficiaryAccountNumber());
                markProcessed(event.getPaymentReferenceNumber(), "CREDITED");
                log.info("UPI {} completed via saga", txn.getUpiTransactionId());
            });
    }

    private void handleDebitFailed(String payload) throws Exception {
        PaymentDebitFailedEvent event = objectMapper.readValue(payload, PaymentDebitFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED")) return;

        upiTransactionRepository.findByUpiTransactionId(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(UpiTransactionStatus.FAILED);
                txn.setFailureReason(event.getFailureReason());
                upiTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED");
                log.warn("UPI {} failed at debit: {}", txn.getUpiTransactionId(), event.getFailureReason());
            });
    }

    private void handleReversed(String payload) throws Exception {
        PaymentReversedEvent event = objectMapper.readValue(payload, PaymentReversedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSED")) return;

        upiTransactionRepository.findByUpiTransactionId(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(UpiTransactionStatus.FAILED);
                txn.setFailureReason("Credit failed — debit reversed");
                upiTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSED");
                log.warn("UPI {} failed — debit reversed successfully", txn.getUpiTransactionId());
            });
    }

    private void handleReversalFailed(String payload) throws Exception {
        PaymentReversalFailedEvent event = objectMapper.readValue(payload, PaymentReversalFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED")) return;

        upiTransactionRepository.findByUpiTransactionId(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(UpiTransactionStatus.RECONCILIATION_REQUIRED);
                txn.setFailureReason("Credit failed and reversal failed — manual reconciliation needed");
                upiTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED");
                log.error("UPI {} requires manual reconciliation", txn.getUpiTransactionId());
            });
    }

    private void writeCompletionOutbox(UpiTransaction txn, String debitAccount, String creditAccount) {
        try {
            TransactionCompletedEvent completedEvent = TransactionCompletedEvent.builder()
                    .transactionId(txn.getId().toString())
                    .paymentMode(PAYMENT_MODE)
                    .paymentReferenceNumber(txn.getUpiTransactionId())
                    .debitAccountNumber(debitAccount)
                    .creditAccountNumber(creditAccount)
                    .amount(txn.getAmount())
                    .status(txn.getStatus().name())
                    .completedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("UpiTransaction");
            outboxEvent.setAggregateId(txn.getUpiTransactionId());
            outboxEvent.setEventType("TransactionCompleted");
            outboxEvent.setTopic(UPI_COMPLETED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(completedEvent));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write completion outbox for UPI {}", txn.getUpiTransactionId(), e);
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
