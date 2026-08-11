package io.voltweave.portfolio.device.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BatteryConfiguration(
        UUID organizationId,
        UUID deviceId,
        BigDecimal capacityKwh,
        BigDecimal maxChargeKw,
        BigDecimal maxDischargeKw,
        int minSocPercent,
        int maxSocPercent,
        BigDecimal efficiency,
        Instant updatedAt
) {
    public BatteryConfiguration {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        requirePositive(capacityKwh, "capacityKwh");
        requirePositive(maxChargeKw, "maxChargeKw");
        requirePositive(maxDischargeKw, "maxDischargeKw");
        if (minSocPercent < 0 || maxSocPercent > 100 || minSocPercent >= maxSocPercent) {
            throw new IllegalArgumentException("SOC range must satisfy 0 <= min < max <= 100");
        }
        if (efficiency == null || efficiency.signum() <= 0
                || efficiency.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("efficiency must be greater than 0 and at most 1");
        }
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
