package io.voltweave.telemetry.processing.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.contracts.events.v1.TelemetryRawPayloadV1;
import io.voltweave.telemetry.processing.application.exception.TelemetryValidationException;
import io.voltweave.telemetry.processing.persistence.TelemetryProcessingRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "voltweave.processing", name = "enabled", havingValue = "true"
)
public class RawTelemetryConsumer {
    static final String CONSUMER_NAME = "telemetry-raw-v1";

    private final ObjectMapper objectMapper;
    private final TelemetryNormalizer normalizer;
    private final TelemetryProcessingRepository repository;
    private final Duration dedupRetention;
    private final Clock clock;

    @Autowired
    public RawTelemetryConsumer(
            ObjectMapper objectMapper,
            TelemetryNormalizer normalizer,
            TelemetryProcessingRepository repository,
            @Value("${voltweave.processing.dedup-retention:7d}") Duration dedupRetention
    ) {
        this(objectMapper, normalizer, repository, dedupRetention, Clock.systemUTC());
    }

    RawTelemetryConsumer(
            ObjectMapper objectMapper,
            TelemetryNormalizer normalizer,
            TelemetryProcessingRepository repository,
            Duration dedupRetention,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.normalizer = normalizer;
        this.repository = repository;
        this.dedupRetention = dedupRetention;
        this.clock = clock;
        if (dedupRetention.isZero() || dedupRetention.isNegative()) {
            throw new IllegalArgumentException("dedup-retention must be positive");
        }
    }

    @KafkaListener(topics = EventTopics.TELEMETRY_RAW_V1, groupId = CONSUMER_NAME)
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        Instant processedAt = clock.instant();
        JsonNode rawJson;
        RawEvent rawEvent;
        try {
            rawJson = objectMapper.readTree(record.value());
            rawEvent = parse(rawJson);
        } catch (Exception exception) {
            quarantineMalformed(record, processedAt, exception);
            return;
        }

        if (!repository.recordEventIfNew(
                CONSUMER_NAME, rawEvent.eventId(), rawEvent.eventType(), processedAt
        )) {
            return;
        }

        try {
            var cursor = repository.latestCursor(
                    rawEvent.organizationId(), rawEvent.payload().deviceId()
            );
            var normalized = normalizer.normalize(
                    rawEvent.organizationId(), rawEvent.payload(),
                    rawEvent.receivedAt(), cursor
            );
            if (!repository.claimSequence(
                    rawEvent.organizationId(), normalized.deviceId(),
                    normalized.sequenceNumber(), normalized.observedAt(),
                    processedAt.plus(dedupRetention)
            )) {
                return;
            }

            var event = EventEnvelopeV1.create(
                    EventTypes.TELEMETRY_NORMALIZED,
                    "telemetry-service",
                    rawEvent.organizationId(),
                    rawEvent.correlationId(),
                    rawEvent.eventId(),
                    normalized.deviceId().toString(),
                    normalized,
                    processedAt
            );
            repository.enqueue(
                    event.eventId(), EventTopics.TELEMETRY_NORMALIZED_V1,
                    event.partitionKey(), objectMapper.writeValueAsString(event),
                    event.occurredAt()
            );
        } catch (TelemetryValidationException exception) {
            repository.quarantine(
                    rawEvent.eventId(), rawEvent.organizationId(),
                    rawEvent.payload().deviceId(), exception.reasonCode(),
                    exception.getMessage(), rawJson.toString(), processedAt
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize normalized telemetry", exception);
        }
    }

    private RawEvent parse(JsonNode event) throws JacksonException {
        if (event == null || !event.isObject()) {
            throw new IllegalArgumentException("Raw event must be a JSON object");
        }
        UUID eventId = uuid(event, "eventId");
        String eventType = event.path("eventType").asString();
        if (event.path("eventVersion").asInt() != 1
                || !EventTypes.TELEMETRY_RAW_RECEIVED.equals(eventType)
                || !"telemetry-service".equals(event.path("producer").asString())) {
            throw new IllegalArgumentException("Unsupported raw telemetry contract");
        }
        UUID organizationId = uuid(event, "tenantId");
        UUID correlationId = uuid(event, "correlationId");
        Instant receivedAt = Instant.parse(event.path("occurredAt").asString());
        var payload = objectMapper.treeToValue(
                event.path("payload"), TelemetryRawPayloadV1.class
        );
        if (!payload.deviceId().toString().equals(event.path("partitionKey").asString())) {
            throw new IllegalArgumentException("Raw event partition key must equal deviceId");
        }
        return new RawEvent(
                eventId, eventType, organizationId, correlationId, receivedAt, payload
        );
    }

    private void quarantineMalformed(
            ConsumerRecord<String, String> record,
            Instant processedAt,
            Exception exception
    ) {
        String rawPayload;
        try {
            JsonNode parsed = objectMapper.readTree(record.value());
            rawPayload = parsed == null ? "null" : parsed.toString();
        } catch (JacksonException ignored) {
            rawPayload = objectMapper.createObjectNode()
                    .put("rawValue", record.value()).toString();
        }
        UUID id = UUID.nameUUIDFromBytes(
                (record.topic() + ':' + record.partition() + ':' + record.offset()).getBytes(UTF_8)
        );
        repository.quarantine(
                id, null, null, "INVALID_RAW_EVENT", exception.getMessage(),
                rawPayload, processedAt
        );
    }

    private static UUID uuid(JsonNode event, String field) {
        return UUID.fromString(event.path(field).asString());
    }

    private record RawEvent(
            UUID eventId,
            String eventType,
            UUID organizationId,
            UUID correlationId,
            Instant receivedAt,
            TelemetryRawPayloadV1 payload
    ) {
    }
}
