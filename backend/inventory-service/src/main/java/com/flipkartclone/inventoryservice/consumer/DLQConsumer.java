package com.flipkartclone.inventoryservice.consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DLQConsumer {

    @KafkaListener(topics = "product-topic.DLT")
    public void handleDLQ(String payload) {

        System.out.println("🚨 DLQ EVENT: " + payload);

        // later:
        // save to DB / alert / manual retry
    }
}