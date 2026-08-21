package com.paystream.accountservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.accountservice.entity.OutboxEvent;
import com.paystream.accountservice.entity.ProcessedEvent;
import com.paystream.accountservice.repository.OutboxEventRepository;
import com.paystream.accountservice.repository.ProcessedEventRepository;
import com.paystream.commonlib.event.PaymentCreditFailedEvent;
import com.paystream.commonlib.event.PaymentDebitedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Writes PaymentSagaConsumer's compensating records in their own transaction.
 *
 * accountService.credit() is itself @Transactional(REQUIRED), so when it throws
 * (e.g. beneficiary not found) Spring's interceptor marks the *shared* transaction
 * rollback-only before control ever returns to the caller's catch block. Writing
 * the PaymentCreditFailed outbox event and the DEBITED processed-marker inside
 * that same transaction would silently roll back along with everything else —
 * this must run via REQUIRES_NEW (called through the Spring proxy, not
 * self-invoked) so it commits independently of the doomed outer transaction.
 */
@Component
@RequiredArgsConstructor
class SagaCompensationWriter {

    private static final String CREDIT_FAILED_TOPIC = "payment.credit.failed";

    private final OutboxEventRepository outboxEventRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCreditFailure(PaymentDebitedEvent event, String ref, String failureReason) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(PaymentCreditFailedEvent.builder()
                    .paymentReferenceNumber(ref)
                    .paymentMode(event.getPaymentMode())
                    .senderAccountNumber(event.getSenderAccountNumber())
                    .beneficiaryAccountNumber(event.getBeneficiaryAccountNumber())
                    .amount(event.getAmount())
                    .failureReason(failureReason)
                    .failedAt(Instant.now())
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize PaymentCreditFailedEvent for " + ref, e);
        }

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Payment");
        outboxEvent.setAggregateId(ref);
        outboxEvent.setEventType("PaymentCreditFailed");
        outboxEvent.setTopic(CREDIT_FAILED_TOPIC);
        outboxEvent.setPayload(payload);
        outboxEventRepository.save(outboxEvent);

        ProcessedEvent pe = new ProcessedEvent();
        pe.setPaymentReferenceNumber(ref);
        pe.setEventType("DEBITED");
        processedEventRepository.save(pe);
    }
}
