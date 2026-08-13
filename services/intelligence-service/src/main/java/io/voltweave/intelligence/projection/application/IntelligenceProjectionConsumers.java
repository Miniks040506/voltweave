package io.voltweave.intelligence.projection.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.portfolio.v1.PortfolioChangeTypeV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioLifecyclePayloadV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioResourceTypeV1;
import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.contracts.events.v1.TelemetryQualityV1;
import io.voltweave.intelligence.projection.persistence.IntelligenceProjectionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(
        prefix = "voltweave.projection", name = "enabled", havingValue = "true"
)
public class IntelligenceProjectionConsumers {
    static final String PORTFOLIO_CONSUMER = "intelligence-portfolio-v1";
    static final String TELEMETRY_CONSUMER = "intelligence-telemetry-v1";
    private static final Set<TelemetryQualityV1> ACCEPTED_QUALITIES = Set.of(
            TelemetryQualityV1.VALID,
            TelemetryQualityV1.STALE,
            TelemetryQualityV1.OUT_OF_ORDER
    );
    private static final Set<String> FLEXIBILITY_DEVICE_TYPES = Set.of(
            "SMART_METER", "BATTERY", "EV_CHARGER"
    );

    private final ObjectMapper objectMapper;
    private final IntelligenceProjectionRepository repository;

    public IntelligenceProjectionConsumers(
            ObjectMapper objectMapper,
            IntelligenceProjectionRepository repository
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @KafkaListener(
            topics = EventTopics.PORTFOLIO_LIFECYCLE_V1,
            groupId = PORTFOLIO_CONSUMER
    )
    @Transactional
    public void consumePortfolio(ConsumerRecord<String, String> record) {
        var envelope = envelope(record, "portfolio-service");
        if (!Set.of(EventTypes.VPP_SITE_ADDED, EventTypes.VPP_SITE_REMOVED)
                .contains(envelope.eventType())) {
            return;
        }
        var payload = value(envelope.payload(), PortfolioLifecyclePayloadV1.class);
        if (payload.resourceType() != PortfolioResourceTypeV1.VPP_MEMBERSHIP
                || payload.relatedResourceId() == null
                || !payload.relatedResourceId().toString().equals(record.key())) {
            throw new IllegalArgumentException("Invalid VPP membership event");
        }
        if (!repository.recordEventIfNew(
                PORTFOLIO_CONSUMER, envelope.eventId(), envelope.eventType(), Instant.now()
        )) {
            return;
        }
        boolean active = switch (envelope.eventType()) {
            case EventTypes.VPP_SITE_ADDED -> {
                if (payload.changeType() != PortfolioChangeTypeV1.ADDED) {
                    throw new IllegalArgumentException("Event type and change type disagree");
                }
                yield true;
            }
            case EventTypes.VPP_SITE_REMOVED -> {
                if (payload.changeType() != PortfolioChangeTypeV1.REMOVED) {
                    throw new IllegalArgumentException("Event type and change type disagree");
                }
                yield false;
            }
            default -> throw new IllegalArgumentException("Unsupported membership event");
        };
        repository.projectVppSite(
                envelope.tenantId(), payload.relatedResourceId(), payload.resourceId(),
                active, envelope.occurredAt()
        );
    }

    @KafkaListener(
            topics = EventTopics.TELEMETRY_NORMALIZED_V1,
            groupId = TELEMETRY_CONSUMER
    )
    @Transactional
    public void consumeTelemetry(ConsumerRecord<String, String> record) {
        var envelope = envelope(record, "telemetry-service");
        if (!EventTypes.TELEMETRY_NORMALIZED.equals(envelope.eventType())) {
            throw new IllegalArgumentException("Unsupported telemetry event type");
        }
        var telemetry = value(envelope.payload(), TelemetryNormalizedPayloadV1.class);
        if (!telemetry.deviceId().toString().equals(record.key())
                || !ACCEPTED_QUALITIES.contains(telemetry.quality())) {
            throw new IllegalArgumentException("Invalid normalized telemetry event");
        }
        String energyType = switch (telemetry.deviceType()) {
            case "SMART_METER" -> "GRID_IMPORT";
            case "SOLAR_INVERTER" -> "SOLAR_GENERATION";
            default -> null;
        };
        boolean flexibilityInput = FLEXIBILITY_DEVICE_TYPES.contains(telemetry.deviceType());
        if (energyType == null && !flexibilityInput) {
            return;
        }
        if (!repository.recordEventIfNew(
                TELEMETRY_CONSUMER, envelope.eventId(), envelope.eventType(), Instant.now()
        )) {
            return;
        }
        if (energyType != null) {
            BigDecimal power = "SOLAR_GENERATION".equals(energyType)
                    ? telemetry.activePowerKw().abs() : telemetry.activePowerKw();
            repository.storeObservation(
                    envelope.tenantId(), telemetry.siteId(), telemetry.deviceId(),
                    telemetry.sequenceNumber(), telemetry.observedAt(), telemetry.receivedAt(),
                    energyType, power, telemetry.quality().name()
            );
        }
        if (flexibilityInput) {
            repository.projectDeviceTelemetry(
                    envelope.tenantId(), telemetry.siteId(), telemetry.deviceId(),
                    telemetry.deviceType(), telemetry.observedAt(), telemetry.receivedAt(),
                    telemetry.activePowerKw(), telemetry.socPercent(), telemetry.online(),
                    telemetry.quality().name()
            );
        }
    }

    private Envelope envelope(ConsumerRecord<String, String> record, String producer) {
        try {
            JsonNode value = objectMapper.readTree(record.value());
            if (value == null || !value.isObject()
                    || value.path("eventVersion").asInt() != 1
                    || !producer.equals(value.path("producer").asString())
                    || !value.path("partitionKey").asString().equals(record.key())) {
                throw new IllegalArgumentException("Unsupported event envelope");
            }
            return new Envelope(
                    UUID.fromString(value.path("eventId").asString()),
                    value.path("eventType").asString(),
                    UUID.fromString(value.path("tenantId").asString()),
                    Instant.parse(value.path("occurredAt").asString()),
                    value.path("payload")
            );
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Event is not valid JSON", exception);
        }
    }

    private <T> T value(JsonNode node, Class<T> type) {
        try {
            return objectMapper.treeToValue(node, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Event payload is invalid", exception);
        }
    }

    private record Envelope(
            UUID eventId,
            String eventType,
            UUID tenantId,
            Instant occurredAt,
            JsonNode payload
    ) {
    }
}
