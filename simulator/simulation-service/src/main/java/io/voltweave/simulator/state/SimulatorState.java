package io.voltweave.simulator.state;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.simulator.config.DeviceType;

public record SimulatorState(
        UUID deviceId,
        DeviceType deviceType,
        long sequenceNumber,
        double activePowerKw,
        double socPercent,
        UUID activeCommandId,
        Instant activeCommandExpiresAt,
        List<AcknowledgementState> recentAcknowledgements
) {
    public SimulatorState {
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(deviceType, "deviceType is required");
        if (sequenceNumber < 0) {
            throw new IllegalArgumentException("sequenceNumber cannot be negative");
        }
        if (!Double.isFinite(activePowerKw) || !Double.isFinite(socPercent)) {
            throw new IllegalArgumentException("device state must be finite");
        }
        if ((activeCommandId == null) != (activeCommandExpiresAt == null)) {
            throw new IllegalArgumentException("active command id and expiry must coexist");
        }
        recentAcknowledgements = List.copyOf(
                recentAcknowledgements == null ? List.of() : recentAcknowledgements
        );
    }

    public record AcknowledgementState(
            UUID commandId,
            String status,
            double appliedPowerKw,
            String reason,
            Instant processedAt
    ) {
    }
}
