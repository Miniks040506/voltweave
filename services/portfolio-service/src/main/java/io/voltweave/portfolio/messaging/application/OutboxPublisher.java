package io.voltweave.portfolio.messaging.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.messaging.persistence.OutboxRepository;

@Service
@ConditionalOnProperty(
        prefix = "voltweave.messaging", name = "enabled", havingValue = "true"
)
public class OutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${voltweave.messaging.outbox.poll-delay}")
    @Transactional
    public void publishReady() {
        for (var event : repository.lockReadyBatch(BATCH_SIZE)) {
            try {
                kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload())
                        .get(10, TimeUnit.SECONDS);
                repository.markPublished(event.eventId(), Instant.now());
            } catch (Exception exception) {
                var message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                var delaySeconds = Math.min(60, 1L << Math.min(event.attempts(), 6));
                repository.markFailed(
                        event.eventId(), Instant.now().plus(delaySeconds, ChronoUnit.SECONDS), message
                );
                LOGGER.warn("Failed to publish outbox event {}", event.eventId(), exception);
            }
        }
    }
}
