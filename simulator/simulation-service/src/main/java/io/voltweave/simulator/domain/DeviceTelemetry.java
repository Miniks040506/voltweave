package io.voltweave.simulator.domain;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.simulator.config.DeviceType;

public record DeviceTelemetry(
        UUID deviceId,
        long sequenceNumber,
        Instant observedAt,
        DeviceType type,
        double activePowerKw,
        Double socPercent,
        boolean online
) {
}
