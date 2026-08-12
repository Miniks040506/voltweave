package io.voltweave.telemetry.processing.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IncomingTelemetry(
        UUID deviceId,
        Long sequenceNumber,
        Instant observedAt,
        String type,
        BigDecimal activePowerKw,
        BigDecimal socPercent,
        Boolean online
) {
}
