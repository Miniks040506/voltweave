package io.voltweave.telemetry.query.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceTwin(
        UUID siteId,
        UUID deviceId,
        String deviceType,
        long sequenceNumber,
        Instant observedAt,
        Instant receivedAt,
        BigDecimal activePowerKw,
        BigDecimal socPercent,
        boolean online,
        String quality,
        Instant updatedAt
) {
}
