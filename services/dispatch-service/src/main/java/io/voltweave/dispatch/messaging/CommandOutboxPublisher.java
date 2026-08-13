package io.voltweave.dispatch.messaging;

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

@Service
@ConditionalOnProperty(prefix = "voltweave.messaging", name = "enabled", havingValue = "true")
class CommandOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandOutboxPublisher.class);
    private static final int BATCH_SIZE = 50;

    private final CommandOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    CommandOutboxPublisher(
            CommandOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate
    ) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${voltweave.messaging.outbox-poll-delay}")
    @Transactional
    public void publishReady() {
        for (var event : repository.lockReady(BATCH_SIZE)) {
            try {
                kafkaTemplate.send(event.topic(), event.partitionKey(), event.payload())
                        .get(10, TimeUnit.SECONDS);
                repository.markPublished(event.eventId(), Instant.now());
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                String message = exception.getMessage() == null
                        ? exception.getClass().getSimpleName() : exception.getMessage();
                long delay = Math.min(60, 1L << Math.min(event.attempts(), 6));
                repository.markFailed(
                        event.eventId(), Instant.now().plus(delay, ChronoUnit.SECONDS), message
                );
                LOGGER.warn("Failed to publish command event {}", event.eventId(), exception);
            }
        }
    }
}
