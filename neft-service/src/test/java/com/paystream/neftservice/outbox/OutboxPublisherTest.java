package com.paystream.neftservice.outbox;

import com.paystream.neftservice.entity.OutboxEvent;
import com.paystream.neftservice.repository.OutboxEventRepository;
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
@DisplayName("NEFT OutboxPublisher unit tests")
class OutboxPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks
    private OutboxPublisher outboxPublisher;

    private OutboxEvent buildEvent(String aggregateId) {
        OutboxEvent e = new OutboxEvent();
        e.setId(UUID.randomUUID());
        e.setAggregateType("NeftTransaction");
        e.setAggregateId(aggregateId);
        e.setEventType("PaymentInitiated");
        e.setTopic("payment.neft.initiated");
        e.setPayload("{\"paymentMode\":\"NEFT\"}");
        e.setPublished(false);
        return e;
    }

    @Test
    @DisplayName("publishes unpublished events and marks them as published")
    void testPublishPendingEvents_publishesAndMarksPublished() throws Exception {
        OutboxEvent event = buildEvent("NEFT202606290001");
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
        OutboxEvent event1 = buildEvent("NEFT202606290001");
        OutboxEvent event2 = buildEvent("NEFT202606290002");
        when(outboxEventRepository.findUnpublishedWithLock()).thenReturn(List.of(event1, event2));

        CompletableFuture<SendResult<String, String>> failFuture = new CompletableFuture<>();
        failFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));

        when(kafkaTemplate.send(anyString(), eq("NEFT202606290001"), anyString())).thenReturn(failFuture);
        when(kafkaTemplate.send(anyString(), eq("NEFT202606290002"), anyString())).thenReturn(successFuture);
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher.publishPendingEvents();

        verify(outboxEventRepository, times(1)).save(any());
        assertThat(event1.isPublished()).isFalse();
        assertThat(event2.isPublished()).isTrue();
    }
}
