package io.voltweave.intelligence.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record EvChargingRequirement(
        double batteryCapacityKwh,
        double currentSocPercent,
        double targetSocPercent,
        double chargerPowerKw,
        double chargingEfficiency) {

    public EvChargingRequirement {
        requirePositive("batteryCapacityKwh", batteryCapacityKwh);
        requirePercent("currentSocPercent", currentSocPercent);
        requirePercent("targetSocPercent", targetSocPercent);
        requirePositive("chargerPowerKw", chargerPowerKw);

        if (targetSocPercent < currentSocPercent) {
            throw new IllegalArgumentException("targetSocPercent must not be below currentSocPercent");
        }
        if (!Double.isFinite(chargingEfficiency)
                || chargingEfficiency <= 0.0
                || chargingEfficiency > 1.0) {
            throw new IllegalArgumentException("chargingEfficiency must be greater than 0 and at most 1");
        }
    }

    public Optional<Instant> latestSafeStart(Instant now, Instant departureTime) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(departureTime, "departureTime must not be null");

        if (!departureTime.isAfter(now)) {
            throw new IllegalArgumentException("departureTime must be after now");
        }

        double requiredEnergyKwh = batteryCapacityKwh
                * (targetSocPercent - currentSocPercent)
                / 100.0;
        double requiredSeconds = requiredEnergyKwh
                / (chargerPowerKw * chargingEfficiency)
                * 3_600.0;

        if (!Double.isFinite(requiredSeconds) || requiredSeconds > Long.MAX_VALUE) {
            return Optional.empty();
        }

        Duration chargingDuration = Duration.ofSeconds((long) Math.ceil(requiredSeconds));
        if (Duration.between(now, departureTime).compareTo(chargingDuration) < 0) {
            return Optional.empty();
        }

        return Optional.of(departureTime.minus(chargingDuration));
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requirePercent(String name, double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }
}
