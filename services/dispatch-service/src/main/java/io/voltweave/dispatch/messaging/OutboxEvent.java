package io.voltweave.dispatch.messaging;

import java.time.Instant;
import java.util.UUID;

record OutboxEvent(
        UUID eventId,
        String topic,
        String partitionKey,
        String payload,
        Instant occurredAt,
        int attempts
) {
}
