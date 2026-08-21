package io.voltweave.simulator.command;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import io.voltweave.simulator.domain.DeviceActionResult;
import io.voltweave.simulator.domain.SimulatedDevice;
import io.voltweave.simulator.state.SimulatorState;

public final class DeviceCommandProcessor {
    private static final int HISTORY_LIMIT = 1_000;

    private final SimulatedDevice device;
    private final Clock clock;
    private final Map<UUID, CommandAcknowledgement> history = new LinkedHashMap<>();
    private UUID activeCommandId;
    private Instant activeCommandExpiresAt;

    public DeviceCommandProcessor(SimulatedDevice device, Clock clock) {
        this(device, clock, null);
    }

    public DeviceCommandProcessor(
            SimulatedDevice device,
            Clock clock,
            SimulatorState restored
    ) {
        this.device = device;
        this.clock = clock;
        if (restored != null) {
            restored.recentAcknowledgements().forEach(saved -> history.put(
                    saved.commandId(), new CommandAcknowledgement(
                            saved.commandId(), saved.status(), saved.appliedPowerKw(),
                            saved.reason(), saved.processedAt()
                    )
            ));
            activeCommandId = restored.activeCommandId();
            activeCommandExpiresAt = restored.activeCommandExpiresAt();
        }
    }

    public synchronized CommandAcknowledgement process(DeviceCommand command) {
        expireActiveCommand();
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
                activeCommandExpiresAt = command.expiresAt();
            }
        }
        remember(command.commandId(), acknowledgement);
        return acknowledgement;
    }

    public synchronized boolean expireActiveCommand() {
        if (activeCommandExpiresAt == null
                || clock.instant().isBefore(activeCommandExpiresAt)) {
            return false;
        }
        device.setPower(0);
        activeCommandId = null;
        activeCommandExpiresAt = null;
        return true;
    }

    public synchronized SimulatorState snapshot() {
        var acknowledgements = history.values().stream()
                .map(value -> new SimulatorState.AcknowledgementState(
                        value.commandId(), value.status(), value.appliedPowerKw(),
                        value.reason(), value.processedAt()
                ))
                .toList();
        return device.snapshot(
                activeCommandId, activeCommandExpiresAt, acknowledgements
        );
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
