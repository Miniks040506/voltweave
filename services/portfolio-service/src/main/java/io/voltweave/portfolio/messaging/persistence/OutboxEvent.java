package io.voltweave.portfolio.messaging.persistence;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String topic,
        String partitionKey,
        String payload,
        Instant occurredAt,
        int attempts
) {
}
