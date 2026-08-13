package io.voltweave.dispatch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.command.v1.CommandRequestedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.dispatch.application.model.DeviceCommand;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.application.model.ReplacementAllocation;
import io.voltweave.dispatch.domain.entities.DispatchState;
import io.voltweave.dispatch.domain.enums.CommandStatus;
import io.voltweave.dispatch.domain.enums.DispatchStatus;
import io.voltweave.dispatch.persistence.CommandRepository;
import io.voltweave.dispatch.persistence.DispatchRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CommandApplicationService {
    private final CommandRepository commandRepository;
    private final DispatchRepository dispatchRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration acknowledgementTimeout;

    @Autowired
    public CommandApplicationService(
            CommandRepository commandRepository,
            DispatchRepository dispatchRepository,
            ObjectMapper objectMapper,
            @Value("${voltweave.command.acknowledgement-timeout:30s}") Duration acknowledgementTimeout
    ) {
        this(commandRepository, dispatchRepository, objectMapper, Clock.systemUTC(),
                acknowledgementTimeout);
    }

    CommandApplicationService(
            CommandRepository commandRepository,
            DispatchRepository dispatchRepository,
            ObjectMapper objectMapper,
            Clock clock,
            Duration acknowledgementTimeout
    ) {
        this.commandRepository = commandRepository;
        this.dispatchRepository = dispatchRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        if (acknowledgementTimeout.isZero() || acknowledgementTimeout.isNegative()) {
            throw new IllegalArgumentException("acknowledgement-timeout must be positive");
        }
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    @Transactional
    public List<DeviceCommand> prepare(UUID dispatchId, UUID correlationId) {
        commandRepository.lockDispatch(dispatchId);
        Dispatch dispatch = dispatchRepository.findById(dispatchId)
                .orElseThrow(() -> new IllegalArgumentException("Dispatch was not found"));
        List<DeviceCommand> existing = commandRepository.findByDispatch(
                dispatch.organizationId(), dispatchId
        );
        if (!existing.isEmpty()) {
            return existing;
        }

        DispatchStatus targetStatus = new DispatchState(dispatch.status())
                .transitionTo(DispatchStatus.PREPARING).status();
        var requestedAt = clock.instant();
        commandRepository.reserve(
                dispatch.organizationId(), dispatch.id(),
                dispatch.allocations().stream().map(Dispatch.Allocation::deviceId).toList(),
                dispatch.scheduledStartAt(), dispatch.scheduledEndAt(), requestedAt
        );
        var acknowledgementDeadline = min(
                dispatch.scheduledStartAt().plus(acknowledgementTimeout),
                dispatch.scheduledEndAt()
        );
        var commands = new ArrayList<DeviceCommand>();
        for (var allocation : dispatch.allocations()) {
            var command = new DeviceCommand(
                    UUID.randomUUID(), dispatch.organizationId(), dispatch.id(),
                    allocation.siteId(), allocation.deviceId(), "SET_POWER",
                    targetPower(allocation), dispatch.scheduledStartAt(),
                    acknowledgementDeadline, dispatch.scheduledEndAt(), CommandStatus.REQUESTED,
                    null, null, requestedAt, null, 0
            );
            var payload = new CommandRequestedPayloadV1(
                    command.id(), dispatch.id(), command.siteId(), command.deviceId(),
                    command.commandType(), command.targetPowerKw(), command.validFrom(),
                    command.acknowledgementDeadlineAt(), command.expiresAt(), null
            );
            var envelope = EventEnvelopeV1.create(
                    EventTypes.COMMAND_REQUESTED, "dispatch-service",
                    dispatch.organizationId(), correlationId, null,
                    command.deviceId().toString(), payload, requestedAt
            );
            commandRepository.insert(
                    command, envelope.eventId(), EventTopics.COMMAND_LIFECYCLE_V1,
                    serialize(envelope)
            );
            commands.add(command);
        }
        commandRepository.transitionDispatch(
                dispatch.organizationId(), dispatch.id(), dispatch.status(),
                targetStatus, dispatch.version()
        );
        return List.copyOf(commands);
    }

    @Transactional
    public List<DeviceCommand> requestReplacements(
            UUID organizationId,
            UUID dispatchId,
            java.time.Instant expiresAt,
            List<ReplacementAllocation> allocations,
            UUID correlationId
    ) {
        var requestedAt = clock.instant();
        commandRepository.reserve(
                organizationId, dispatchId,
                allocations.stream().map(ReplacementAllocation::deviceId).toList(),
                requestedAt, expiresAt, requestedAt
        );
        var deadline = min(requestedAt.plus(acknowledgementTimeout), expiresAt);
        var commands = new ArrayList<DeviceCommand>();
        for (var allocation : allocations) {
            var command = new DeviceCommand(
                    UUID.randomUUID(), organizationId, dispatchId,
                    allocation.siteId(), allocation.deviceId(), "SET_POWER",
                    targetPower(allocation.deviceType(), allocation.sourceAvailablePowerKw(),
                            allocation.allocatedPowerKw()),
                    requestedAt, deadline, expiresAt, CommandStatus.REQUESTED,
                    null, null, requestedAt, null, 0
            );
            var payload = new CommandRequestedPayloadV1(
                    command.id(), dispatchId, command.siteId(), command.deviceId(),
                    command.commandType(), command.targetPowerKw(), command.validFrom(),
                    command.acknowledgementDeadlineAt(), command.expiresAt(), null
            );
            var envelope = EventEnvelopeV1.create(
                    EventTypes.COMMAND_REQUESTED, "dispatch-service", organizationId,
                    correlationId, null, command.deviceId().toString(), payload, requestedAt
            );
            commandRepository.insert(
                    command, envelope.eventId(), EventTopics.COMMAND_LIFECYCLE_V1,
                    serialize(envelope)
            );
            commands.add(command);
        }
        return List.copyOf(commands);
    }

    private static BigDecimal targetPower(Dispatch.Allocation allocation) {
        return targetPower(
                allocation.deviceType(), allocation.sourceAvailablePowerKw(),
                allocation.allocatedPowerKw()
        );
    }

    private static BigDecimal targetPower(
            String deviceType,
            BigDecimal sourceAvailablePowerKw,
            BigDecimal allocatedPowerKw
    ) {
        return (switch (deviceType) {
            case "BATTERY" -> allocatedPowerKw.negate();
            case "EV_CHARGER" -> sourceAvailablePowerKw.subtract(allocatedPowerKw);
            default -> throw new IllegalStateException(
                    "Unsupported controllable device type: " + deviceType
            );
        }).setScale(3, RoundingMode.HALF_UP);
    }

    private static java.time.Instant min(java.time.Instant first, java.time.Instant second) {
        return first.isBefore(second) ? first : second;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize command event", exception);
        }
    }
}
