package io.voltweave.dispatch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.command.v1.CommandRequestedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.dispatch.application.model.DeviceCommand;
import io.voltweave.dispatch.application.model.Dispatch;
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

    @Autowired
    public CommandApplicationService(
            CommandRepository commandRepository,
            DispatchRepository dispatchRepository,
            ObjectMapper objectMapper
    ) {
        this(commandRepository, dispatchRepository, objectMapper, Clock.systemUTC());
    }

    CommandApplicationService(
            CommandRepository commandRepository,
            DispatchRepository dispatchRepository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.commandRepository = commandRepository;
        this.dispatchRepository = dispatchRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
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
        var commands = new ArrayList<DeviceCommand>();
        for (var allocation : dispatch.allocations()) {
            var command = new DeviceCommand(
                    UUID.randomUUID(), dispatch.organizationId(), dispatch.id(),
                    allocation.siteId(), allocation.deviceId(), "SET_POWER",
                    targetPower(allocation), dispatch.scheduledStartAt(),
                    dispatch.scheduledEndAt(), CommandStatus.REQUESTED,
                    null, null, requestedAt, null, 0
            );
            var payload = new CommandRequestedPayloadV1(
                    command.id(), dispatch.id(), command.siteId(), command.deviceId(),
                    command.commandType(), command.targetPowerKw(), command.validFrom(),
                    command.expiresAt(), null
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

    private static BigDecimal targetPower(Dispatch.Allocation allocation) {
        return (switch (allocation.deviceType()) {
            case "BATTERY" -> allocation.allocatedPowerKw().negate();
            case "EV_CHARGER" -> allocation.sourceAvailablePowerKw()
                    .subtract(allocation.allocatedPowerKw());
            default -> throw new IllegalStateException(
                    "Unsupported controllable device type: " + allocation.deviceType()
            );
        }).setScale(3, RoundingMode.HALF_UP);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize command event", exception);
        }
    }
}
