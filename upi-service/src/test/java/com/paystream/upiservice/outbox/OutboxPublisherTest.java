package com.paystream.upiservice.outbox;

import com.paystream.upiservice.entity.OutboxEvent;
import com.paystream.upiservice.repository.OutboxEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UPI OutboxPublisher unit tests")
class OutboxPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private OutboxEvent buildEvent(String topic) {
        OutboxEvent e = new OutboxEvent();
        e.setId(UUID.randomUUID());
        e.setAggregateType("UpiTransaction");
        e.setAggregateId("UPI123456789");
        e.setEventType("PaymentInitiated");
        e.setTopic(topic);
        e.setPayload("{\"paymentMode\":\"UPI\"}");
        e.setPublished(false);
        return e;
    }

    @Test
    @DisplayName("publishes unpublished events and marks them as published")
    void testPublishPendingEvents_publishesAndMarksPublished() throws Exception {
        OutboxEvent event = buildEvent("payment.upi.initiated");
        when(outboxEventRepository.findUnpublishedWithLock()).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(future);
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().isPublished()).isTrue();
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("skips publishing when no unpublished events exist")
    void testPublishPendingEvents_noEvents_doesNothing() {
        when(outboxEventRepository.findUnpublishedWithLock()).thenReturn(List.of());

        outboxPublisher.publishPendingEvents();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
        verify(outboxEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("continues processing remaining events if one Kafka send fails")
    void testPublishPendingEvents_kafkaFailure_continuesOtherEvents() throws Exception {
        OutboxEvent event1 = buildEvent("payment.upi.initiated");
        OutboxEvent event2 = buildEvent("payment.upi.initiated");
        event2.setAggregateId("UPI999888777");
        when(outboxEventRepository.findUnpublishedWithLock()).thenReturn(List.of(event1, event2));

        // First send fails, second succeeds
        CompletableFuture<SendResult<String, String>> failFuture = new CompletableFuture<>();
        failFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(anyString(), eq("UPI123456789"), anyString())).thenReturn(failFuture);
        when(kafkaTemplate.send(anyString(), eq("UPI999888777"), anyString())).thenReturn(successFuture);
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher.publishPendingEvents();

        // Only the successful event gets marked published
        verify(outboxEventRepository, times(1)).save(any());
        assertThat(event1.isPublished()).isFalse();
        assertThat(event2.isPublished()).isTrue();
    }
}
