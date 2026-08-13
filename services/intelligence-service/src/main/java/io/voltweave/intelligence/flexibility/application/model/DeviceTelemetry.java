package io.voltweave.intelligence.flexibility.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DeviceTelemetry(
        UUID deviceId,
        UUID siteId,
        String deviceType,
        Instant observedAt,
        Instant receivedAt,
        BigDecimal activePowerKw,
        BigDecimal socPercent,
        boolean online,
        String quality
) {
}
