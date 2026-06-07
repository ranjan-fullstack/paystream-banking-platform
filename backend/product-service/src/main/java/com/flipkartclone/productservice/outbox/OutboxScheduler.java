package com.flipkartclone.productservice.outbox;

import com.flipkartclone.productservice.model.OutboxEvent;
import com.flipkartclone.productservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {

    private final OutboxRepository outboxRepository;
    private final OutboxPublisher publisher;

    @Scheduled(fixedDelay = 5000)
    public void publishEvents() {

        List<OutboxEvent> events =
                outboxRepository.findTop10ByStatusOrderByCreatedAtAsc("NEW");

        for (OutboxEvent event : events) {
            try {
                publisher.publish(event.getPayload());

                event.setStatus("SENT");
                outboxRepository.save(event);

                System.out.println("✅ Event sent: " + event.getId());

            } catch (Exception e) {
                System.out.println("❌ Failed: " + event.getId());
            }
        }
    }
}