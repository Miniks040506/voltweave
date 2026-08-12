package io.voltweave.telemetry.processing.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.telemetry.processing.persistence.OutboxEvent;
import io.voltweave.telemetry.processing.persistence.TelemetryProcessingRepository;

class TelemetryProcessingJobsTests {
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    private final TelemetryProcessingRepository repository =
            mock(TelemetryProcessingRepository.class);
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private final TelemetryProcessingJobs jobs = new TelemetryProcessingJobs(
            repository, kafkaTemplate, Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void marksOutboxPublishedOnlyAfterKafkaAcknowledges() {
        var event = event(0);
        var result = new SendResult<String, String>(
                new ProducerRecord<>(event.topic(), event.partitionKey(), event.payload()), null
        );
        when(repository.lockReadyOutbox(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(result));

        jobs.publishReady();

        verify(repository).markPublished(event.eventId(), NOW);
        verify(repository, never()).markFailed(any(), any(), anyString());
    }

    @Test
    void schedulesFailedPublicationForRetry() {
        var event = event(2);
        when(repository.lockReadyOutbox(100)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("offline")));

        jobs.publishReady();

        verify(repository).markFailed(event.eventId(), NOW.plusSeconds(4), "offline");
        verify(repository, never()).markPublished(any(), any());
    }

    @Test
    void deletesExpiredDedupRowsInBoundedBatches() {
        when(repository.deleteExpiredDedup(NOW, 100)).thenReturn(100, 25);

        jobs.deleteExpiredDedup();

        verify(repository, org.mockito.Mockito.times(2)).deleteExpiredDedup(NOW, 100);
    }

    private static OutboxEvent event(int attempts) {
        return new OutboxEvent(
                UUID.randomUUID(), EventTopics.TELEMETRY_NORMALIZED_V1,
                UUID.randomUUID().toString(), "{}", NOW, attempts
        );
    }
}
