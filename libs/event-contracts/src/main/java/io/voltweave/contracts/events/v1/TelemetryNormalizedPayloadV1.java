package io.voltweave.contracts.events.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TelemetryNormalizedPayloadV1(
        UUID siteId,
        UUID deviceId,
        long sequenceNumber,
        Instant observedAt,
        Instant receivedAt,
        String deviceType,
        BigDecimal activePowerKw,
        BigDecimal socPercent,
        boolean online,
        TelemetryQualityV1 quality
) {
    public TelemetryNormalizedPayloadV1 {
        Objects.requireNonNull(siteId, "siteId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(observedAt, "observedAt is required");
        Objects.requireNonNull(receivedAt, "receivedAt is required");
        Objects.requireNonNull(activePowerKw, "activePowerKw is required");
        Objects.requireNonNull(quality, "quality is required");
        deviceType = requireText(deviceType, "deviceType", 32);
        if (sequenceNumber <= 0) {
            throw new IllegalArgumentException("sequenceNumber must be positive");
        }
        if (receivedAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("receivedAt must not precede observedAt");
        }
        if (socPercent != null
                && (socPercent.signum() < 0 || socPercent.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw new IllegalArgumentException("socPercent must be between 0 and 100");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
