package io.voltweave.telemetry.command.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.command.v1.CommandRequestedPayloadV1;
import io.voltweave.telemetry.command.persistence.CommandGatewayRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@ConditionalOnProperty(prefix = "voltweave.command", name = "enabled", havingValue = "true")
public class CommandRequestedConsumer {
    static final String CONSUMER_NAME = "telemetry-command-gateway-v1";

    private final ObjectMapper objectMapper;
    private final CommandGatewayRepository repository;
    private final Clock clock;

    @Autowired
    public CommandRequestedConsumer(ObjectMapper objectMapper, CommandGatewayRepository repository) {
        this(objectMapper, repository, Clock.systemUTC());
    }

    CommandRequestedConsumer(
            ObjectMapper objectMapper,
            CommandGatewayRepository repository,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.clock = clock;
    }

    @KafkaListener(topics = EventTopics.COMMAND_LIFECYCLE_V1, groupId = CONSUMER_NAME)
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            if (!EventTypes.COMMAND_REQUESTED.equals(event.path("eventType").asString())) {
                return;
            }
            ParsedCommand parsed = parse(record, event);
            if (!repository.recordEventIfNew(
                    parsed.eventId(), EventTypes.COMMAND_REQUESTED, clock.instant()
            )) {
                return;
            }
            String mqttPayload = objectMapper.writeValueAsString(new MqttCommand(
                    parsed.payload().commandId(), parsed.payload().commandType(),
                    parsed.payload().targetPowerKw(), parsed.payload().expiresAt(),
                    parsed.payload().supersedesCommandId()
            ));
            repository.insert(
                    parsed.organizationId(), parsed.correlationId(), parsed.eventId(),
                    parsed.payload(), mqttPayload, clock.instant()
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid command.requested event", exception);
        }
    }

    private ParsedCommand parse(
            ConsumerRecord<String, String> record,
            JsonNode event
    ) throws Exception {
        String producer = event.path("producer").asString();
        if (event.path("eventVersion").asInt() != 1 || !"dispatch-service".equals(producer)) {
            throw new IllegalArgumentException("Unsupported command contract");
        }
        UUID eventId = uuid(event, "eventId");
        UUID organizationId = uuid(event, "tenantId");
        UUID correlationId = uuid(event, "correlationId");
        var payload = objectMapper.treeToValue(
                event.path("payload"), CommandRequestedPayloadV1.class
        );
        String deviceKey = payload.deviceId().toString();
        if (!deviceKey.equals(event.path("partitionKey").asString())
                || !deviceKey.equals(record.key())) {
            throw new IllegalArgumentException("Command partition key must equal deviceId");
        }
        return new ParsedCommand(eventId, organizationId, correlationId, payload);
    }

    private static UUID uuid(JsonNode event, String field) {
        return UUID.fromString(event.path(field).asString());
    }

    private record ParsedCommand(
            UUID eventId,
            UUID organizationId,
            UUID correlationId,
            CommandRequestedPayloadV1 payload
    ) {
    }

    private record MqttCommand(
            UUID commandId,
            String commandType,
            BigDecimal targetPowerKw,
            Instant expiresAt,
            UUID supersedesCommandId
    ) {
    }
}
