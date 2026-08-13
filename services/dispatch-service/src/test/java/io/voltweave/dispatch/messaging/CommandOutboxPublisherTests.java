package io.voltweave.dispatch.messaging;

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

class CommandOutboxPublisherTests {
    private final CommandOutboxRepository repository = mock(CommandOutboxRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final CommandOutboxPublisher publisher = new CommandOutboxPublisher(
            repository, kafkaTemplate
    );

    @Test
    void marksBrokerAcknowledgedEventAsPublished() {
        var event = event(0);
        when(repository.lockReady(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishReady();

        verify(repository).markPublished(eq(event.eventId()), any(Instant.class));
    }

    @Test
    void leavesBrokerFailureReadyForBackoffRetry() {
        var event = event(2);
        when(repository.lockReady(50)).thenReturn(List.of(event));
        when(kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        publisher.publishReady();

        verify(repository).markFailed(
                eq(event.eventId()), any(Instant.class),
                eq("java.lang.RuntimeException: broker down")
        );
    }

    private static OutboxEvent event(int attempts) {
        return new OutboxEvent(
                UUID.randomUUID(), "vw.command.lifecycle.v1", UUID.randomUUID().toString(),
                "{}", Instant.now(), attempts
        );
    }
}
