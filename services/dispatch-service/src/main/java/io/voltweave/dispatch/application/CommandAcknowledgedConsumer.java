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
import io.voltweave.contracts.events.command.v1.CommandAcknowledgedPayloadV1;
import io.voltweave.dispatch.domain.enums.CommandStatus;
import io.voltweave.dispatch.persistence.CommandRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommandAcknowledgedConsumer {
    private final ObjectMapper objectMapper;
    private final CommandRepository repository;
    private final Clock clock;

    @Autowired
    public CommandAcknowledgedConsumer(ObjectMapper objectMapper, CommandRepository repository) {
        this(objectMapper, repository, Clock.systemUTC());
    }

    CommandAcknowledgedConsumer(
            ObjectMapper objectMapper,
            CommandRepository repository,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.clock = clock;
    }

    @KafkaListener(
            topics = EventTopics.COMMAND_LIFECYCLE_V1,
            groupId = CommandRepository.ACK_CONSUMER
    )
    @Transactional
    public void consume(ConsumerRecord<String, String> record) {
        try {
            JsonNode event = objectMapper.readTree(record.value());
            if (!EventTypes.COMMAND_ACKNOWLEDGED.equals(event.path("eventType").asString())) {
                return;
            }
            ParsedAcknowledgement parsed = parse(record, event);
            if (!repository.recordAcknowledgementIfNew(
                    parsed.eventId(), EventTypes.COMMAND_ACKNOWLEDGED, clock.instant()
            )) {
                return;
            }
            var command = repository.lockById(
                    parsed.organizationId(), parsed.payload().commandId()
            ).orElseThrow(() -> new IllegalArgumentException("Unknown commandId"));
            validateOwnership(command.dispatchId(), command.siteId(), command.deviceId(), parsed);
            if (command.status() != CommandStatus.REQUESTED) {
                return;
            }
            CommandStatus status = CommandStatus.valueOf(parsed.payload().status());
            repository.acknowledge(
                    command.id(), status, parsed.payload().appliedPowerKw(),
                    parsed.payload().reason(), clock.instant()
            );
            if (status == CommandStatus.ACCEPTED) {
                repository.activateWhenAllCommandsAccepted(
                        parsed.organizationId(), command.dispatchId()
                );
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid command.acknowledged event", exception);
        }
    }

    private ParsedAcknowledgement parse(
            ConsumerRecord<String, String> record,
            JsonNode event
    ) throws Exception {
        if (event.path("eventVersion").asInt() != 1
                || !"telemetry-service".equals(event.path("producer").asString())) {
            throw new IllegalArgumentException("Unsupported acknowledgement contract");
        }
        var payload = objectMapper.treeToValue(
                event.path("payload"), CommandAcknowledgedPayloadV1.class
        );
        String deviceKey = payload.deviceId().toString();
        if (!deviceKey.equals(event.path("partitionKey").asString())
                || !deviceKey.equals(record.key())) {
            throw new IllegalArgumentException("Acknowledgement partition key must equal deviceId");
        }
        return new ParsedAcknowledgement(
                uuid(event, "eventId"), uuid(event, "tenantId"), payload
        );
    }

    private static void validateOwnership(
            UUID dispatchId,
            UUID siteId,
            UUID deviceId,
            ParsedAcknowledgement parsed
    ) {
        var payload = parsed.payload();
        if (!dispatchId.equals(payload.dispatchId()) || !siteId.equals(payload.siteId())
                || !deviceId.equals(payload.deviceId())) {
            throw new IllegalArgumentException("Acknowledgement does not own command");
        }
    }

    private static UUID uuid(JsonNode event, String field) {
        return UUID.fromString(event.path(field).asString());
    }

    private record ParsedAcknowledgement(
            UUID eventId,
            UUID organizationId,
            CommandAcknowledgedPayloadV1 payload
    ) {
    }
}
