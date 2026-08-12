package io.voltweave.telemetry.query.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.telemetry.query.application.model.TelemetryPoint;

public record TelemetryPointResponse(
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
    public static TelemetryPointResponse from(TelemetryPoint point) {
        return new TelemetryPointResponse(
                point.deviceId(), point.sequenceNumber(), point.observedAt(),
                point.receivedAt(), point.deviceType(), point.activePowerKw(),
                point.socPercent(), point.online(), point.quality()
        );
    }
}
