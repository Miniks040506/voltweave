package io.voltweave.simulator.config;

import java.util.Objects;
import java.util.UUID;

public record DeviceScenario(
        UUID deviceId,
        DeviceType type,
        MqttCredential mqtt,
        double ratedPowerKw,
        double initialSocPercent,
        double capacityKwh,
        double minSocPercent,
        double maxSocPercent,
        double efficiency,
        long seed
) {
    public DeviceScenario {
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(mqtt, "mqtt is required");
        requirePositive(ratedPowerKw, "ratedPowerKw");
        if (type == DeviceType.BATTERY || type == DeviceType.EV_CHARGER) {
            requireRange(initialSocPercent, 0, 100, "initialSocPercent");
            requirePositive(capacityKwh, "capacityKwh");
            requireRange(minSocPercent, 0, 100, "minSocPercent");
            requireRange(maxSocPercent, 0, 100, "maxSocPercent");
            if (minSocPercent >= maxSocPercent
                    || initialSocPercent < minSocPercent
                    || initialSocPercent > maxSocPercent) {
                throw new IllegalArgumentException("SOC limits must contain initialSocPercent");
            }
            if (efficiency <= 0 || efficiency > 1) {
                throw new IllegalArgumentException("efficiency must be in (0, 1]");
            }
        }
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requireRange(double value, double min, double max, String field) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(field + " must be between " + min + " and " + max);
        }
    }
}
