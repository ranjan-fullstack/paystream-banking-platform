package com.paystream.transactionservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.commonlib.event.PaymentCreditedEvent;
import com.paystream.commonlib.event.PaymentReversedEvent;
import com.paystream.commonlib.event.TransactionCompletedEvent;
import com.paystream.transactionservice.entity.Transaction;
import com.paystream.transactionservice.enums.PaymentMode;
import com.paystream.transactionservice.enums.TransactionStatus;
import com.paystream.transactionservice.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Posts a ledger entry to the central ledger whenever any payment rail
 * (NEFT/RTGS/IMPS/UPI) finishes processing a transfer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionEventConsumer {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = {
            "payment.neft.completed",
            "payment.rtgs.settled",
            "payment.imps.completed",
            "payment.upi.completed"
    }, groupId = "${spring.kafka.consumer.group-id:transaction-service-group}")
    @Transactional
    public void onPaymentCompleted(String payload) {
        try {
            TransactionCompletedEvent event = objectMapper.readValue(payload, TransactionCompletedEvent.class);
            recordTransaction(event);
        } catch (Exception e) {
            log.error("Failed to process payment completion event: {}", payload, e);
        }
    }

    @KafkaListener(topics = "payment.credited",
                   groupId = "${spring.kafka.consumer.group-id:transaction-service-group}")
    @Transactional
    public void onPaymentCredited(String payload) {
        try {
            PaymentCreditedEvent event = objectMapper.readValue(payload, PaymentCreditedEvent.class);
            if (transactionRepository.existsByPaymentReferenceNumber(event.getPaymentReferenceNumber())) return;

            Transaction txn = buildTransaction(
                    event.getPaymentMode(),
                    event.getPaymentReferenceNumber(),
                    event.getSenderAccountNumber(),
                    event.getBeneficiaryAccountNumber(),
                    event.getAmount(),
                    TransactionStatus.COMPLETED
            );
            transactionRepository.save(txn);
            log.info("Saga: recorded COMPLETED ledger entry for {} {}", event.getPaymentMode(), event.getPaymentReferenceNumber());
        } catch (Exception e) {
            log.error("Failed to process payment.credited event: {}", payload, e);
        }
    }

    @KafkaListener(topics = {"payment.reversed", "payment.reconciliation.required"},
                   groupId = "${spring.kafka.consumer.group-id:transaction-service-group}")
    @Transactional
    public void onPaymentFailed(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            PaymentReversedEvent event = objectMapper.readValue(payload, PaymentReversedEvent.class);
            if (transactionRepository.existsByPaymentReferenceNumber(event.getPaymentReferenceNumber())) return;

            TransactionStatus status = "payment.reconciliation.required".equals(topic)
                    ? TransactionStatus.FAILED : TransactionStatus.FAILED;

            Transaction txn = buildTransaction(
                    event.getPaymentMode(),
                    event.getPaymentReferenceNumber(),
                    event.getSenderAccountNumber(),
                    null,
                    event.getAmount(),
                    status
            );
            transactionRepository.save(txn);
            log.info("Saga: recorded FAILED ledger entry for {} {} (topic={})",
                    event.getPaymentMode(), event.getPaymentReferenceNumber(), topic);
        } catch (Exception e) {
            log.error("Failed to process {} event: {}", topic, payload, e);
        }
    }

    private void recordTransaction(TransactionCompletedEvent event) {
        if (transactionRepository.existsByPaymentReferenceNumber(event.getPaymentReferenceNumber())) {
            log.info("Ledger entry already exists for reference {}, skipping", event.getPaymentReferenceNumber());
            return;
        }

        Transaction txn = buildTransaction(
                event.getPaymentMode(),
                event.getPaymentReferenceNumber(),
                event.getDebitAccountNumber(),
                event.getCreditAccountNumber(),
                event.getAmount(),
                parseStatus(event.getStatus())
        );
        transactionRepository.save(txn);
        log.info("Recorded ledger entry {} for {} reference {}", txn.getTransactionId(), event.getPaymentMode(), event.getPaymentReferenceNumber());
    }

    private Transaction buildTransaction(String paymentMode, String reference,
                                          String debitAccount, String creditAccount,
                                          java.math.BigDecimal amount, TransactionStatus status) {
        Transaction txn = new Transaction();
        txn.setTransactionId(generateUniqueTransactionId());
        txn.setPaymentMode(PaymentMode.valueOf(paymentMode));
        txn.setPaymentReferenceNumber(reference);
        txn.setDebitAccountNumber(debitAccount);
        txn.setCreditAccountNumber(creditAccount);
        txn.setAmount(amount);
        txn.setStatus(status);
        txn.setInitiatedAt(LocalDateTime.now());
        txn.setCompletedAt(LocalDateTime.now());
        txn.setDescription(paymentMode + " transfer via " + reference);
        return txn;
    }

    private TransactionStatus parseStatus(String rawStatus) {
        try {
            return TransactionStatus.valueOf(rawStatus);
        } catch (Exception e) {
            return TransactionStatus.COMPLETED;
        }
    }

    private String generateUniqueTransactionId() {
        String datePart = LocalDateTime.now().format(DATE_FMT);
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "TXN" + datePart + String.format("%05d", RANDOM.nextInt(100000));
            if (!transactionRepository.existsByTransactionId(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique transaction id, please retry");
    }
}
