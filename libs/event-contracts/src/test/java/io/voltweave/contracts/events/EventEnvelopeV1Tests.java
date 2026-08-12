package io.voltweave.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.contracts.events.v1.EventEnvelopeV1;

class EventEnvelopeV1Tests {
    @Test
    void createsNormalizedVersionOneEnvelope() {
        UUID tenantId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-08-12T03:00:00Z");

        var envelope = EventEnvelopeV1.create(
                "SitePreferenceUpdated", "portfolio-service", tenantId,
                correlationId, null, "site-1", "payload", occurredAt
        );

        assertThat(envelope.eventId()).isNotNull();
        assertThat(envelope.eventVersion()).isEqualTo(1);
        assertThat(envelope.tenantId()).isEqualTo(tenantId);
        assertThat(envelope.correlationId()).isEqualTo(correlationId);
        assertThat(envelope.occurredAt()).isEqualTo(occurredAt);
    }

    @Test
    void rejectsInvalidEnvelopeAtConstruction() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new EventEnvelopeV1<>(
                id, "Event", 2, Instant.now(), "producer", id, id,
                null, "key", "payload"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventVersion 1");

        assertThatThrownBy(() -> EventEnvelopeV1.create(
                " ", "producer", id, id, null, "key", "payload", Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void topicNamesAreUniqueAndVersioned() {
        assertThat(new HashSet<>(EventTopics.V1_TOPICS))
                .hasSameSizeAs(EventTopics.V1_TOPICS);
        assertThat(EventTopics.V1_TOPICS).allMatch(topic -> topic.endsWith(".v1"));
    }
}
