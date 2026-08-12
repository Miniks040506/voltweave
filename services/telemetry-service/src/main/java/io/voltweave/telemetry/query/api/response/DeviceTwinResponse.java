package io.voltweave.telemetry.query.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.telemetry.query.application.model.DeviceTwin;

public record DeviceTwinResponse(
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
    public static DeviceTwinResponse from(DeviceTwin twin) {
        return new DeviceTwinResponse(
                twin.siteId(), twin.deviceId(), twin.deviceType(),
                twin.sequenceNumber(), twin.observedAt(), twin.receivedAt(),
                twin.activePowerKw(), twin.socPercent(), twin.online(),
                twin.quality(), twin.updatedAt()
        );
    }
}
