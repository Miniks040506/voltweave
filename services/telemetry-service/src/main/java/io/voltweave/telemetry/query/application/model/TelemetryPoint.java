package io.voltweave.telemetry.query.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryPoint(
        UUID deviceId,
        long sequenceNumber,
        Instant observedAt,
        Instant receivedAt,
        String deviceType,
        BigDecimal activePowerKw,
        BigDecimal socPercent,
        boolean online,
        String quality
) {
}
