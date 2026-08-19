package io.voltweave.telemetry.command.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.command.v1.CommandAcknowledgedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.telemetry.command.persistence.CommandGatewayRepository;
import io.voltweave.telemetry.processing.persistence.TelemetryProcessingRepository;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommandAcknowledgementService {
    private final ObjectMapper objectMapper;
    private final CommandGatewayRepository commandRepository;
    private final TelemetryProcessingRepository outboxRepository;
    private final Clock clock;

    @Autowired
    public CommandAcknowledgementService(
            ObjectMapper objectMapper,
            CommandGatewayRepository commandRepository,
            TelemetryProcessingRepository outboxRepository
    ) {
        this(objectMapper, commandRepository, outboxRepository, Clock.systemUTC());
    }

    CommandAcknowledgementService(
            ObjectMapper objectMapper,
            CommandGatewayRepository commandRepository,
            TelemetryProcessingRepository outboxRepository,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.commandRepository = commandRepository;
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional
    public void receive(String topic, byte[] payload) {
        try {
            TopicIdentity topicIdentity = parseTopic(topic);
            DeviceAcknowledgement acknowledgement = objectMapper.readValue(
                    payload, DeviceAcknowledgement.class
            );
            var command = commandRepository.findIdentity(acknowledgement.commandId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown commandId"));
            if (!command.organizationId().equals(topicIdentity.organizationId())
                    || !command.siteId().equals(topicIdentity.siteId())
                    || !command.deviceId().equals(topicIdentity.deviceId())) {
                throw new IllegalArgumentException("Acknowledgement topic does not own command");
            }
            var receivedAt = clock.instant();
            if (commandRepository.timeOutIfExpired(
                    acknowledgement.commandId(), receivedAt
            )) {
                return;
            }
            if (!commandRepository.acknowledge(
                    acknowledgement.commandId(), receivedAt
            )) {
                return;
            }

            var eventPayload = new CommandAcknowledgedPayloadV1(
                    acknowledgement.commandId(), command.dispatchId(), command.siteId(),
                    command.deviceId(), acknowledgement.status(),
                    BigDecimal.valueOf(acknowledgement.appliedPowerKw()),
                    acknowledgement.reason(), acknowledgement.processedAt()
            );
            var event = EventEnvelopeV1.create(
                    EventTypes.COMMAND_ACKNOWLEDGED, "telemetry-service",
                    command.organizationId(), command.correlationId(),
                    command.requestedEventId(), command.deviceId().toString(),
                    eventPayload, clock.instant()
            );
            outboxRepository.enqueue(
                    event.eventId(), EventTopics.COMMAND_LIFECYCLE_V1,
                    event.partitionKey(), objectMapper.writeValueAsString(event),
                    event.occurredAt()
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid command acknowledgement", exception);
        }
    }

    private static TopicIdentity parseTopic(String topic) {
        String[] parts = topic == null ? new String[0] : topic.split("/", -1);
        if (parts.length != 5 || !"voltweave".equals(parts[0]) || !"ack".equals(parts[4])) {
            throw new IllegalArgumentException("Invalid acknowledgement topic");
        }
        return new TopicIdentity(
                UUID.fromString(parts[1]), UUID.fromString(parts[2]), UUID.fromString(parts[3])
        );
    }

    private record TopicIdentity(UUID organizationId, UUID siteId, UUID deviceId) {
    }

    private record DeviceAcknowledgement(
            UUID commandId,
            String status,
            double appliedPowerKw,
            String reason,
            Instant processedAt
    ) {
    }
}
