package io.voltweave.portfolio.device.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvChargerConfiguration(
        UUID organizationId,
        UUID deviceId,
        BigDecimal maxChargingKw,
        BigDecimal vehicleBatteryCapacityKwh,
        int targetSocPercent,
        BigDecimal chargingEfficiency,
        Instant departureAt,
        Instant updatedAt
) {
    public EvChargerConfiguration {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        requirePositive(maxChargingKw, "maxChargingKw");
        requirePositive(vehicleBatteryCapacityKwh, "vehicleBatteryCapacityKwh");
        if (targetSocPercent < 1 || targetSocPercent > 100) {
            throw new IllegalArgumentException("targetSocPercent must be between 1 and 100");
        }
        if (chargingEfficiency == null || chargingEfficiency.signum() <= 0
                || chargingEfficiency.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(
                    "chargingEfficiency must be greater than 0 and at most 1"
            );
        }
        Objects.requireNonNull(departureAt, "departureAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
