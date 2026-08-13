package io.voltweave.dispatch.application;

import java.time.Clock;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.contracts.events.v1.TelemetryQualityV1;
import io.voltweave.dispatch.persistence.PerformanceRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class NormalizedTelemetryPerformanceConsumer {
    private final ObjectMapper objectMapper;
    private final PerformanceRepository repository;
    private final PerformanceApplicationService performanceService;
    private final Clock clock;

    @Autowired
    public NormalizedTelemetryPerformanceConsumer(
            ObjectMapper objectMapper,
            PerformanceRepository repository,
            PerformanceApplicationService performanceService
    ) {
        this(objectMapper, repository, performanceService, Clock.systemUTC());
    }

    NormalizedTelemetryPerformanceConsumer(
            ObjectMapper objectMapper,
            PerformanceRepository repository,
            PerformanceApplicationService performanceService,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.performanceService = performanceService;
        this.clock = clock;
    }

    @KafkaListener(
            topics = EventTopics.TELEMETRY_NORMALIZED_V1,
            groupId = PerformanceRepository.CONSUMER_NAME
    )
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            ParsedTelemetry parsed = parse(record, event);
            if (parsed.telemetry().quality() != TelemetryQualityV1.VALID) {
                return;
            }
            if (!repository.recordEventIfNew(
                    parsed.eventId(), EventTypes.TELEMETRY_NORMALIZED, clock.instant()
            )) {
                return;
            }
            performanceService.record(
                    parsed.eventId(), parsed.organizationId(), parsed.telemetry(), clock.instant()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid telemetry.normalized event", exception);
        }
    }

    private ParsedTelemetry parse(ConsumerRecord<String, String> record, JsonNode event)
            throws Exception {
        if (event == null || !event.isObject()
                || event.path("eventVersion").asInt() != 1
                || !EventTypes.TELEMETRY_NORMALIZED.equals(event.path("eventType").asString())
                || !"telemetry-service".equals(event.path("producer").asString())) {
            throw new IllegalArgumentException("Unsupported normalized telemetry contract");
        }
        UUID eventId = UUID.fromString(event.path("eventId").asString());
        UUID organizationId = UUID.fromString(event.path("tenantId").asString());
        var telemetry = objectMapper.treeToValue(
                event.path("payload"), TelemetryNormalizedPayloadV1.class
        );
        String deviceKey = telemetry.deviceId().toString();
        if (!deviceKey.equals(event.path("partitionKey").asString())
                || !deviceKey.equals(record.key())) {
            throw new IllegalArgumentException("Normalized event key must equal deviceId");
        }
        return new ParsedTelemetry(eventId, organizationId, telemetry);
    }

    private record ParsedTelemetry(
            UUID eventId,
            UUID organizationId,
            TelemetryNormalizedPayloadV1 telemetry
    ) {
    }
}
