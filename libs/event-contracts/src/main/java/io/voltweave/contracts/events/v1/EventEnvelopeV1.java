package io.voltweave.contracts.events.v1;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EventEnvelopeV1<T>(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String producer,
        UUID tenantId,
        UUID correlationId,
        UUID causationId,
        String partitionKey,
        T payload
) {
    public EventEnvelopeV1 {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(tenantId, "tenantId is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        Objects.requireNonNull(payload, "payload is required");
        eventType = requireText(eventType, "eventType", 120);
        producer = requireText(producer, "producer", 80);
        partitionKey = requireText(partitionKey, "partitionKey", 255);
        if (eventVersion != 1) {
            throw new IllegalArgumentException("EventEnvelopeV1 requires eventVersion 1");
        }
    }

    public static <T> EventEnvelopeV1<T> create(
            String eventType,
            String producer,
            UUID tenantId,
            UUID correlationId,
            UUID causationId,
            String partitionKey,
            T payload,
            Instant occurredAt
    ) {
        return new EventEnvelopeV1<>(
                UUID.randomUUID(), eventType, 1, occurredAt, producer, tenantId,
                correlationId, causationId, partitionKey, payload
        );
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
