package com.paystream.rtgsservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.rtgsservice.entity.OutboxEvent;
import com.paystream.rtgsservice.entity.ProcessedEvent;
import com.paystream.rtgsservice.entity.RtgsTransaction;
import com.paystream.rtgsservice.enums.RtgsStatus;
import com.paystream.rtgsservice.repository.OutboxEventRepository;
import com.paystream.rtgsservice.repository.ProcessedEventRepository;
import com.paystream.rtgsservice.repository.RtgsTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

@Component
@RequiredArgsConstructor
@Slf4j
public class RtgsSagaConsumer {

    private static final String PAYMENT_MODE = "RTGS";
    private static final String RTGS_SETTLED_TOPIC = "payment.rtgs.settled";

    private final RtgsTransactionRepository rtgsTransactionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = {"payment.credited", "payment.debit.failed", "payment.reversed", "payment.reversal.failed"},
        groupId = "${spring.kafka.consumer.group-id:rtgs-service-group}"
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
            log.error("RtgsSagaConsumer failed to process event from topic {}: {}", topic, payload, e);
        }
    }

    private void handleCredited(String payload) throws Exception {
        PaymentCreditedEvent event = objectMapper.readValue(payload, PaymentCreditedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "CREDITED")) return;

        rtgsTransactionRepository.findByRtgsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(RtgsStatus.COMPLETED);
                txn.setRbiUtrNumber(generateUtr());
                txn.setSettledAt(LocalDateTime.now());
                rtgsTransactionRepository.save(txn);

                writeSettlementOutbox(txn, event.getSenderAccountNumber(), event.getBeneficiaryAccountNumber());
                markProcessed(event.getPaymentReferenceNumber(), "CREDITED");
                log.info("RTGS {} settled via saga", txn.getRtgsReferenceNumber());
            });
    }

    private void handleDebitFailed(String payload) throws Exception {
        PaymentDebitFailedEvent event = objectMapper.readValue(payload, PaymentDebitFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED")) return;

        rtgsTransactionRepository.findByRtgsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(RtgsStatus.FAILED);
                txn.setFailureReason(event.getFailureReason());
                rtgsTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "DEBIT_FAILED");
                log.warn("RTGS {} failed at debit: {}", txn.getRtgsReferenceNumber(), event.getFailureReason());
            });
    }

    private void handleReversed(String payload) throws Exception {
        PaymentReversedEvent event = objectMapper.readValue(payload, PaymentReversedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSED")) return;

        rtgsTransactionRepository.findByRtgsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(RtgsStatus.FAILED);
                txn.setFailureReason("Credit failed — debit reversed");
                rtgsTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSED");
                log.warn("RTGS {} failed — debit reversed successfully", txn.getRtgsReferenceNumber());
            });
    }

    private void handleReversalFailed(String payload) throws Exception {
        PaymentReversalFailedEvent event = objectMapper.readValue(payload, PaymentReversalFailedEvent.class);
        if (!PAYMENT_MODE.equals(event.getPaymentMode())) return;
        if (isAlreadyProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED")) return;

        rtgsTransactionRepository.findByRtgsReferenceNumber(event.getPaymentReferenceNumber())
            .ifPresent(txn -> {
                txn.setStatus(RtgsStatus.RECONCILIATION_REQUIRED);
                txn.setFailureReason("Credit failed and reversal failed — manual reconciliation needed");
                rtgsTransactionRepository.save(txn);
                markProcessed(event.getPaymentReferenceNumber(), "REVERSAL_FAILED");
                log.error("RTGS {} requires manual reconciliation", txn.getRtgsReferenceNumber());
            });
    }

    private void writeSettlementOutbox(RtgsTransaction txn, String debitAccount, String creditAccount) {
        try {
            TransactionCompletedEvent completedEvent = TransactionCompletedEvent.builder()
                    .transactionId(txn.getId().toString())
                    .paymentMode(PAYMENT_MODE)
                    .paymentReferenceNumber(txn.getRtgsReferenceNumber())
                    .debitAccountNumber(debitAccount)
                    .creditAccountNumber(creditAccount)
                    .amount(txn.getAmount())
                    .status(txn.getStatus().name())
                    .completedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("RtgsTransaction");
            outboxEvent.setAggregateId(txn.getRtgsReferenceNumber());
            outboxEvent.setEventType("TransactionCompleted");
            outboxEvent.setTopic(RTGS_SETTLED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(completedEvent));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write settlement outbox for RTGS {}", txn.getRtgsReferenceNumber(), e);
        }
    }

    private String generateUtr() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String seq = String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000));
        return "PAYSR" + date + seq; // 5 + 8 + 9 = 22 chars
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
