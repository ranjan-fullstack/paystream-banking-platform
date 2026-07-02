package com.paystream.neftservice.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentInitiatedEvent;
import com.paystream.neftservice.entity.NeftBatch;
import com.paystream.neftservice.entity.NeftTransaction;
import com.paystream.neftservice.entity.OutboxEvent;
import com.paystream.neftservice.enums.NeftBatchStatus;
import com.paystream.neftservice.enums.NeftStatus;
import com.paystream.neftservice.repository.NeftBatchRepository;
import com.paystream.neftservice.repository.NeftTransactionRepository;
import com.paystream.neftservice.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * RBI NEFT settlement runs in half-hourly batches, Monday-Saturday 8 AM-7 PM.
 * This job picks up QUEUED transactions and kicks off a saga per transaction by
 * writing a payment.neft.initiated outbox event. Actual debit/credit and
 * final status updates happen asynchronously via NeftSagaConsumer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NeftBatchScheduler {

    private static final String NEFT_INITIATED_TOPIC = "payment.neft.initiated";
    private static final DateTimeFormatter BATCH_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final NeftTransactionRepository neftTransactionRepository;
    private final NeftBatchRepository neftBatchRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "0 0,30 8-19 * * MON-SAT")
    @Transactional
    public void processNeftBatch() {
        List<NeftTransaction> queued = neftTransactionRepository.findByStatus(NeftStatus.QUEUED);
        if (queued.isEmpty()) {
            return;
        }

        NeftBatch batch = new NeftBatch();
        batch.setBatchNumber("BATCH-" + LocalDateTime.now().format(BATCH_FMT));
        batch.setScheduledAt(LocalDateTime.now());
        batch.setStatus(NeftBatchStatus.PROCESSING);
        batch.setTotalTransactions(queued.size());
        batch = neftBatchRepository.save(batch);

        int initiatedCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (NeftTransaction txn : queued) {
            txn.setStatus(NeftStatus.BATCH_PROCESSING);
            txn.setBatchId(batch.getBatchNumber());
            txn.setBatchProcessedAt(LocalDateTime.now());

            writeInitiatedOutbox(txn);
            initiatedCount++;
            totalAmount = totalAmount.add(txn.getAmount());
        }
        neftTransactionRepository.saveAll(queued);

        batch.setProcessedAt(LocalDateTime.now());
        batch.setSuccessCount(initiatedCount);
        batch.setFailureCount(0);
        batch.setTotalAmount(totalAmount);
        batch.setStatus(NeftBatchStatus.COMPLETED);
        neftBatchRepository.save(batch);

        log.info("NEFT batch {} initiated {} saga(s) totalling {}",
                batch.getBatchNumber(), initiatedCount, totalAmount);
    }

    private void writeInitiatedOutbox(NeftTransaction txn) {
        try {
            PaymentInitiatedEvent event = PaymentInitiatedEvent.builder()
                    .paymentReferenceNumber(txn.getNeftReferenceNumber())
                    .paymentMode("NEFT")
                    .senderAccountNumber(txn.getSenderAccountNumber())
                    .beneficiaryAccountNumber(txn.getBeneficiaryAccountNumber())
                    .amount(txn.getAmount())
                    .initiatedAt(Instant.now())
                    .build();

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setAggregateType("NeftTransaction");
            outboxEvent.setAggregateId(txn.getNeftReferenceNumber());
            outboxEvent.setEventType("PaymentInitiated");
            outboxEvent.setTopic(NEFT_INITIATED_TOPIC);
            outboxEvent.setPayload(objectMapper.writeValueAsString(event));
            outboxEventRepository.save(outboxEvent);
        } catch (Exception e) {
            log.error("Failed to write initiated outbox event for NEFT {}", txn.getNeftReferenceNumber(), e);
        }
    }
}
