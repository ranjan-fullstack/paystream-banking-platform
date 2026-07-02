package com.paystream.accountservice.consumer;

import com.paystream.accountservice.entity.PaymentDeadLetter;
import com.paystream.accountservice.repository.PaymentDeadLetterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class DltConsumer {

    private final PaymentDeadLetterRepository deadLetterRepository;

    @KafkaListener(
        topics = {
            "payment.neft.initiated.DLT",
            "payment.rtgs.initiated.DLT",
            "payment.imps.initiated.DLT",
            "payment.upi.initiated.DLT",
            "payment.debited.DLT",
            "payment.credited.DLT",
            "payment.credit.failed.DLT",
            "payment.reversed.DLT"
        },
        groupId = "${spring.kafka.consumer.group-id:account-service-group}"
    )
    @Transactional
    public void onDeadLetter(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = "kafka_dlt-exception-message", required = false) byte[] exceptionMessageBytes) {

        String failureReason = exceptionMessageBytes != null
                ? new String(exceptionMessageBytes, StandardCharsets.UTF_8)
                : "Unknown — see logs for original exception";

        String originalTopic = topic.endsWith(".DLT") ? topic.substring(0, topic.length() - 4) : topic;

        log.error("DLT message received from [{}]: failureReason='{}', payload='{}'",
                topic, failureReason, payload);

        PaymentDeadLetter deadLetter = new PaymentDeadLetter();
        deadLetter.setOriginalTopic(originalTopic);
        deadLetter.setPayload(payload);
        deadLetter.setFailureReason(failureReason);
        deadLetter.setFailedAt(LocalDateTime.now());
        deadLetter.setStatus("PENDING_REVIEW");

        deadLetterRepository.save(deadLetter);
    }
}
