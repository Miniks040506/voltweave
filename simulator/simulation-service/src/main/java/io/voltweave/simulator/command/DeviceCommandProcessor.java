package io.voltweave.simulator.command;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.voltweave.simulator.domain.DeviceActionResult;
import io.voltweave.simulator.domain.SimulatedDevice;

public final class DeviceCommandProcessor {
    private static final int HISTORY_LIMIT = 1_000;

    private final SimulatedDevice device;
    private final Clock clock;
    private final Map<UUID, CommandAcknowledgement> history = new LinkedHashMap<>();
    private UUID activeCommandId;

    public DeviceCommandProcessor(SimulatedDevice device, Clock clock) {
        this.device = device;
        this.clock = clock;
    }

    public synchronized CommandAcknowledgement process(DeviceCommand command) {
        var previous = history.get(command.commandId());
        if (previous != null) {
            return previous;
        }

        CommandAcknowledgement acknowledgement;
        if (!clock.instant().isBefore(command.expiresAt())) {
            acknowledgement = rejected(command, "command expired");
        } else if (!"SET_POWER".equals(command.commandType())) {
            acknowledgement = rejected(command, "unsupported command type");
        } else if (activeCommandId != null
                && !activeCommandId.equals(command.supersedesCommandId())) {
            acknowledgement = rejected(command, "active command must be explicitly superseded");
        } else {
            acknowledgement = action(command, device.setPower(command.targetPowerKw()));
            if ("ACCEPTED".equals(acknowledgement.status())) {
                activeCommandId = command.commandId();
            }
        }
        remember(command.commandId(), acknowledgement);
        return acknowledgement;
    }

    private CommandAcknowledgement action(DeviceCommand command, DeviceActionResult result) {
        return new CommandAcknowledgement(
                command.commandId(), result.accepted() ? "ACCEPTED" : "REJECTED",
                result.appliedPowerKw(), result.reason(), clock.instant()
        );
    }

    private CommandAcknowledgement rejected(DeviceCommand command, String reason) {
        return new CommandAcknowledgement(
                command.commandId(), "REJECTED", 0, reason, clock.instant()
        );
    }

    private void remember(UUID commandId, CommandAcknowledgement acknowledgement) {
        history.put(commandId, acknowledgement);
        if (history.size() > HISTORY_LIMIT) {
            history.remove(history.keySet().iterator().next());
        }
    }
}
