package com.flipkartclone.productservice.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void publish(String payload) {
        kafkaTemplate.send("product-topic", payload);
    }
}
