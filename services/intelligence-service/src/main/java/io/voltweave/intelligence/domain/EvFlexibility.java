package io.voltweave.intelligence.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record EvFlexibility(
        EvChargingRequirement chargingRequirement,
        double currentChargingPowerKw,
        boolean online,
        boolean telemetryFresh,
        boolean optedIn,
        boolean reserved
) {
    public EvFlexibility {
        Objects.requireNonNull(chargingRequirement, "chargingRequirement is required");
        if (!Double.isFinite(currentChargingPowerKw) || currentChargingPowerKw < 0.0) {
            throw new IllegalArgumentException(
                    "currentChargingPowerKw must be finite and non-negative"
            );
        }
    }

    public double curtailablePowerKw(
            Instant now,
            Instant departureAt,
            Duration dispatchDuration
    ) {
        Objects.requireNonNull(now, "now is required");
        Objects.requireNonNull(departureAt, "departureAt is required");
        if (dispatchDuration == null
                || dispatchDuration.isZero()
                || dispatchDuration.isNegative()) {
            throw new IllegalArgumentException("dispatchDuration must be positive");
        }
        if (!online || !telemetryFresh || !optedIn || reserved) {
            return 0.0;
        }

        return chargingRequirement.latestSafeStart(now, departureAt)
                .filter(latestStart -> !now.plus(dispatchDuration).isAfter(latestStart))
                .map(ignored -> Math.min(
                        currentChargingPowerKw,
                        chargingRequirement.chargerPowerKw()
                ))
                .orElse(0.0);
    }
}
