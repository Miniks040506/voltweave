package io.voltweave.telemetry.processing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.telemetry.processing.persistence.TelemetryProcessingRepository;

@Service
@ConditionalOnProperty(
        prefix = "voltweave.processing", name = "enabled", havingValue = "true"
)
public class TelemetryProcessingJobs {
    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryProcessingJobs.class);
    private static final int BATCH_SIZE = 100;

    private final TelemetryProcessingRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    @Autowired
    public TelemetryProcessingJobs(
            TelemetryProcessingRepository repository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this(repository, kafkaTemplate, Clock.systemUTC());
    }

    TelemetryProcessingJobs(
            TelemetryProcessingRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            Clock clock
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${voltweave.processing.outbox-poll-delay:1s}")
    @Transactional
    public void publishReady() {
        for (var event : repository.lockReadyOutbox(BATCH_SIZE)) {
            try {
                kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload())
                        .get(10, TimeUnit.SECONDS);
                repository.markPublished(event.eventId(), clock.instant());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                Throwable failure = exception instanceof ExecutionException && exception.getCause() != null
                        ? exception.getCause() : exception;
                String message = failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage();
                long delaySeconds = Math.min(60, 1L << Math.min(event.attempts(), 6));
                repository.markFailed(
                        event.eventId(),
                        clock.instant().plus(delaySeconds, ChronoUnit.SECONDS),
                        message
                );
                LOGGER.warn("Failed to publish normalized telemetry {}", event.eventId(), failure);
            }
        }
    }

    @Scheduled(fixedDelayString = "${voltweave.processing.dedup-cleanup-delay:1h}")
    @Transactional
    public void deleteExpiredDedup() {
        int deleted;
        do {
            deleted = repository.deleteExpiredDedup(clock.instant(), BATCH_SIZE);
        } while (deleted == BATCH_SIZE);
    }
}
