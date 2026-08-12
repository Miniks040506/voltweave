package io.voltweave.portfolio.messaging.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import io.voltweave.portfolio.messaging.persistence.OutboxEvent;
import io.voltweave.portfolio.messaging.persistence.OutboxRepository;

class OutboxPublisherTests {
    private final OutboxRepository repository = mock(OutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final OutboxPublisher publisher = new OutboxPublisher(repository, kafkaTemplate);

    @Test
    void marksAnAcknowledgedEventAsPublished() {
        var event = event(0);
        when(repository.lockReadyBatch(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishReady();

        verify(repository).markPublished(eq(event.eventId()), any(Instant.class));
    }

    @Test
    void keepsAFailedEventForRetry() {
        var event = event(3);
        when(repository.lockReadyBatch(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher.publishReady();

        verify(repository).markFailed(
                eq(event.eventId()), any(Instant.class), eq("java.lang.RuntimeException: broker down")
        );
    }

    private static OutboxEvent event(int attempts) {
        return new OutboxEvent(
                UUID.randomUUID(), "vw.audit.v1", "tenant-1", "{}", Instant.now(), attempts
        );
    }
}
