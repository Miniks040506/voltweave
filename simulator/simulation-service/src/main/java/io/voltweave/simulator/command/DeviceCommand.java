package io.voltweave.simulator.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DeviceCommand(
        UUID commandId,
        String commandType,
        double targetPowerKw,
        Instant expiresAt,
        UUID supersedesCommandId
) {
    public DeviceCommand {
        Objects.requireNonNull(commandId, "commandId is required");
        if (commandType == null || commandType.isBlank()) {
            throw new IllegalArgumentException("commandType is required");
        }
        commandType = commandType.trim();
        if (!Double.isFinite(targetPowerKw)) {
            throw new IllegalArgumentException("targetPowerKw must be finite");
        }
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }
}
