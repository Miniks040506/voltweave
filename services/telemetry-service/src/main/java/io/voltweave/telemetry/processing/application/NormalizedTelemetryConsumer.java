package io.voltweave.telemetry.processing.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.contracts.events.v1.TelemetryQualityV1;
import io.voltweave.telemetry.processing.persistence.TelemetryProcessingRepository;
import io.voltweave.telemetry.realtime.SiteTelemetryBroadcaster;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "voltweave.processing", name = "enabled", havingValue = "true"
)
public class NormalizedTelemetryConsumer {
    static final String CONSUMER_NAME = "telemetry-storage-v1";
    private static final Set<String> DEVICE_TYPES = Set.of(
            "SMART_METER", "SOLAR_INVERTER", "BATTERY", "EV_CHARGER"
    );
    private static final Set<TelemetryQualityV1> ACCEPTED_QUALITIES = Set.of(
            TelemetryQualityV1.VALID,
            TelemetryQualityV1.STALE,
            TelemetryQualityV1.OUT_OF_ORDER
    );

    private final ObjectMapper objectMapper;
    private final TelemetryProcessingRepository repository;
    private final SiteTelemetryBroadcaster broadcaster;
    private final Clock clock;

    @Autowired
    public NormalizedTelemetryConsumer(
            ObjectMapper objectMapper,
            TelemetryProcessingRepository repository,
            SiteTelemetryBroadcaster broadcaster
    ) {
        this(objectMapper, repository, broadcaster, Clock.systemUTC());
    }

    NormalizedTelemetryConsumer(
            ObjectMapper objectMapper,
            TelemetryProcessingRepository repository,
            SiteTelemetryBroadcaster broadcaster,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.broadcaster = broadcaster;
        this.clock = clock;
    }

    @KafkaListener(topics = EventTopics.TELEMETRY_NORMALIZED_V1, groupId = CONSUMER_NAME)
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        NormalizedEvent event = parse(record);
        Instant processedAt = clock.instant();
        if (!repository.recordEventIfNew(
                CONSUMER_NAME, event.eventId(), event.eventType(), processedAt
        )) {
            return;
        }
        if (repository.storeAcceptedPoint(event.organizationId(), event.telemetry())
                && repository.advanceTwin(
                        event.organizationId(), event.telemetry(), processedAt
                )) {
            publishAfterCommit(event);
        }
    }

    private void publishAfterCommit(NormalizedEvent event) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        broadcaster.publish(event.organizationId(), event.telemetry());
                    }
                }
        );
    }

    private NormalizedEvent parse(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            if (event == null || !event.isObject()) {
                throw unsupported("Normalized event must be a JSON object");
            }
            String eventType = event.path("eventType").asString();
            if (event.path("eventVersion").asInt() != 1
                    || !EventTypes.TELEMETRY_NORMALIZED.equals(eventType)
                    || !"telemetry-service".equals(event.path("producer").asString())) {
                throw unsupported("Unsupported normalized telemetry contract");
            }
            UUID eventId = uuid(event, "eventId");
            UUID organizationId = uuid(event, "tenantId");
            Instant.parse(event.path("occurredAt").asString());
            var telemetry = objectMapper.treeToValue(
                    event.path("payload"), TelemetryNormalizedPayloadV1.class
            );
            String partitionKey = event.path("partitionKey").asString();
            if (!telemetry.deviceId().toString().equals(partitionKey)
                    || !partitionKey.equals(record.key())) {
                throw unsupported("Normalized event key must equal deviceId");
            }
            if (!DEVICE_TYPES.contains(telemetry.deviceType())
                    || !ACCEPTED_QUALITIES.contains(telemetry.quality())) {
                throw unsupported("Normalized telemetry is not accepted storage data");
            }
            return new NormalizedEvent(eventId, eventType, organizationId, telemetry);
        } catch (JacksonException exception) {
            throw unsupported("Normalized event is not valid JSON", exception);
        }
    }

    private static UUID uuid(JsonNode event, String field) {
        return UUID.fromString(event.path(field).asString());
    }

    private static IllegalArgumentException unsupported(String message) {
        return new IllegalArgumentException(message);
    }

    private static IllegalArgumentException unsupported(String message, Throwable cause) {
        return new IllegalArgumentException(message, cause);
    }

    private record NormalizedEvent(
            UUID eventId,
            String eventType,
            UUID organizationId,
            TelemetryNormalizedPayloadV1 telemetry
    ) {
    }
}
