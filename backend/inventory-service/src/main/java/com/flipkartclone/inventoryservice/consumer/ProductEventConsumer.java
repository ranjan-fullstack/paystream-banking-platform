package com.flipkartclone.inventoryservice.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flipkartclone.common.event.ProductCreatedEvent;
import com.flipkartclone.inventoryservice.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductEventConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "product-topic",
            groupId = "inventory-group"
    )
    public void consume(String payload) {

        try {
            ProductCreatedEvent event =
                    objectMapper.readValue(payload, ProductCreatedEvent.class);

            inventoryService.createStock(event);

        } catch (Exception e) {
            // ❗ VERY IMPORTANT → throw exception to trigger retry
            throw new RuntimeException("Processing failed", e);
        }
    }
}