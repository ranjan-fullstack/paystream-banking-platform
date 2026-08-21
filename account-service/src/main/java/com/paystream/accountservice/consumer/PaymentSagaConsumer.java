package com.paystream.accountservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.accountservice.dto.AmountRequest;
import com.paystream.accountservice.entity.OutboxEvent;
import com.paystream.accountservice.entity.ProcessedEvent;
import com.paystream.accountservice.enums.PaymentMode;
import com.paystream.accountservice.repository.OutboxEventRepository;
import com.paystream.accountservice.repository.ProcessedEventRepository;
import com.paystream.accountservice.service.AccountService;
import com.paystream.commonlib.event.PaymentCreditFailedEvent;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentDebitFailedEvent;
import com.paystream.commonlib.event.PaymentDebitedEvent;
import com.paystream.commonlib.event.PaymentInitiatedEvent;
import com.paystream.commonlib.event.PaymentReversalFailedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Choreography Saga executor for the payment settlement flow.
 *
 * payment.{mode}.initiated  → debit sender  → payment.debited | payment.debit.failed
 * payment.debited           → credit beneficiary → payment.credited | payment.credit.failed
 * payment.credit.failed     → reverse debit → payment.reversed | payment.reversal.failed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSagaConsumer {

    private static final String DEBITED_TOPIC         = "payment.debited";
    private static final String DEBIT_FAILED_TOPIC    = "payment.debit.failed";
    private static final String CREDITED_TOPIC        = "payment.credited";
    private static final String REVERSED_TOPIC        = "payment.reversed";
    private static final String REVERSAL_FAILED_TOPIC = "payment.reversal.failed";
    private static final String RECONCILIATION_TOPIC  = "payment.reconciliation.required";

    private final AccountService accountService;
    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;
    private final SagaCompensationWriter sagaCompensationWriter;

    // ─── Step 1: Debit sender ─────────────────────────────────────────────────

    @KafkaListener(
        topics = {
            "payment.upi.initiated",
            "payment.neft.initiated",
            "payment.rtgs.initiated",
            "payment.imps.initiated"
        },
        groupId = "${spring.kafka.consumer.group-id:account-service-group}"
    )
    @Transactional
    public void onPaymentInitiated(String payload) {
        try {
            PaymentInitiatedEvent event = objectMapper.readValue(payload, PaymentInitiatedEvent.class);
            String ref = event.getPaymentReferenceNumber();

            if (isAlreadyProcessed(ref, "INITIATED")) return;

            try {
                AmountRequest debitReq = amountRequest(event.getAmount(), ref, event.getPaymentMode());
                accountService.debit(event.getSenderAccountNumber(), debitReq);

                writeOutbox(ref, "PaymentDebited", DEBITED_TOPIC,
                        objectMapper.writeValueAsString(PaymentDebitedEvent.builder()
                                .paymentReferenceNumber(ref)
                                .paymentMode(event.getPaymentMode())
                                .senderAccountNumber(event.getSenderAccountNumber())
                                .beneficiaryAccountNumber(event.getBeneficiaryAccountNumber())
                                .amount(event.getAmount())
                                .debitedAt(Instant.now())
                                .build()));
                log.info("Debited {} for {} {}", event.getSenderAccountNumber(), event.getPaymentMode(), ref);
            } catch (Exception e) {
                writeOutbox(ref, "PaymentDebitFailed", DEBIT_FAILED_TOPIC,
                        objectMapper.writeValueAsString(PaymentDebitFailedEvent.builder()
                                .paymentReferenceNumber(ref)
                                .paymentMode(event.getPaymentMode())
                                .senderAccountNumber(event.getSenderAccountNumber())
                                .beneficiaryAccountNumber(event.getBeneficiaryAccountNumber())
                                .amount(event.getAmount())
                                .failureReason(e.getMessage())
                                .failedAt(Instant.now())
                                .build()));
                log.warn("Debit failed for {} {}: {}", event.getPaymentMode(), ref, e.getMessage());
            }

            markProcessed(ref, "INITIATED");
        } catch (Exception e) {
            log.error("PaymentSagaConsumer failed processing payment.initiated: {}", payload, e);
        }
    }

    // ─── Step 2: Credit beneficiary ───────────────────────────────────────────

    @KafkaListener(
        topics = "payment.debited",
        groupId = "${spring.kafka.consumer.group-id:account-service-group}"
    )
    @Transactional
    public void onPaymentDebited(String payload) {
        try {
            PaymentDebitedEvent event = objectMapper.readValue(payload, PaymentDebitedEvent.class);
            String ref = event.getPaymentReferenceNumber();

            if (isAlreadyProcessed(ref, "DEBITED")) return;

            try {
                AmountRequest creditReq = amountRequest(event.getAmount(), ref, event.getPaymentMode());
                accountService.credit(event.getBeneficiaryAccountNumber(), creditReq);

                writeOutbox(ref, "PaymentCredited", CREDITED_TOPIC,
                        objectMapper.writeValueAsString(PaymentCreditedEvent.builder()
                                .paymentReferenceNumber(ref)
                                .paymentMode(event.getPaymentMode())
                                .senderAccountNumber(event.getSenderAccountNumber())
                                .beneficiaryAccountNumber(event.getBeneficiaryAccountNumber())
                                .amount(event.getAmount())
                                .creditedAt(Instant.now())
                                .build()));
                log.info("Credited {} for {} {}", event.getBeneficiaryAccountNumber(), event.getPaymentMode(), ref);
                markProcessed(ref, "DEBITED");
            } catch (Exception e) {
                log.warn("Credit failed for {} {}: {}", event.getPaymentMode(), ref, e.getMessage());
                // accountService.credit() above already marked this method's shared transaction
                // rollback-only, so the compensating write runs in its own REQUIRES_NEW
                // transaction — otherwise it would silently vanish along with everything else
                // when this transaction rolls back on commit.
                sagaCompensationWriter.recordCreditFailure(event, ref, e.getMessage());
            }
        } catch (Exception e) {
            log.error("PaymentSagaConsumer failed processing payment.debited: {}", payload, e);
        }
    }

    // ─── Step 3: Compensating transaction — reverse the debit ─────────────────

    @KafkaListener(
        topics = "payment.credit.failed",
        groupId = "${spring.kafka.consumer.group-id:account-service-group}"
    )
    @Transactional
    public void onCreditFailed(String payload) {
        try {
            PaymentCreditFailedEvent event = objectMapper.readValue(payload, PaymentCreditFailedEvent.class);
            String ref = event.getPaymentReferenceNumber();

            if (isAlreadyProcessed(ref, "CREDIT_FAILED")) return;

            try {
                // Reverse the debit by crediting the original sender back (no limit tracking on reversal)
                AmountRequest reverseReq = amountRequest(event.getAmount(), ref, null);
                accountService.credit(event.getSenderAccountNumber(), reverseReq);

                writeOutbox(ref, "PaymentReversed", REVERSED_TOPIC,
                        objectMapper.writeValueAsString(PaymentReversedEvent.builder()
                                .paymentReferenceNumber(ref)
                                .paymentMode(event.getPaymentMode())
                                .senderAccountNumber(event.getSenderAccountNumber())
                                .amount(event.getAmount())
                                .reversedAt(Instant.now())
                                .build()));
                log.info("Debit reversed for {} {}", event.getPaymentMode(), ref);
            } catch (Exception e) {
                writeOutbox(ref, "PaymentReversalFailed", REVERSAL_FAILED_TOPIC,
                        objectMapper.writeValueAsString(PaymentReversalFailedEvent.builder()
                                .paymentReferenceNumber(ref)
                                .paymentMode(event.getPaymentMode())
                                .senderAccountNumber(event.getSenderAccountNumber())
                                .amount(event.getAmount())
                                .failureReason(e.getMessage())
                                .failedAt(Instant.now())
                                .build()));
                // Also flag for manual reconciliation
                writeOutbox(ref, "PaymentReconciliationRequired", RECONCILIATION_TOPIC,
                        objectMapper.writeValueAsString(PaymentReversalFailedEvent.builder()
                                .paymentReferenceNumber(ref)
                                .paymentMode(event.getPaymentMode())
                                .senderAccountNumber(event.getSenderAccountNumber())
                                .amount(event.getAmount())
                                .failureReason("Reversal failed after credit failure: " + e.getMessage())
                                .failedAt(Instant.now())
                                .build()));
                log.error("Reversal failed for {} {} — requires reconciliation", event.getPaymentMode(), ref);
            }

            markProcessed(ref, "CREDIT_FAILED");
        } catch (Exception e) {
            log.error("PaymentSagaConsumer failed processing payment.credit.failed: {}", payload, e);
        }
    }

    private AmountRequest amountRequest(java.math.BigDecimal amount, String reference, String paymentModeStr) {
        AmountRequest req = new AmountRequest();
        req.setAmount(amount);
        req.setReferenceNumber(reference);
        if (paymentModeStr != null) {
            try {
                req.setPaymentMode(PaymentMode.valueOf(paymentModeStr));
            } catch (IllegalArgumentException ignored) {
                // unknown mode — leave null, limit check will be skipped
            }
        }
        return req;
    }

    private void writeOutbox(String aggregateId, String eventType, String topic, String payload) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Payment");
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setEventType(eventType);
        outboxEvent.setTopic(topic);
        outboxEvent.setPayload(payload);
        outboxEventRepository.save(outboxEvent);
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
