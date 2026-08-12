package io.voltweave.portfolio.vpp.domain.entities;

import java.math.BigDecimal;
import java.util.Objects;

public record VppInstalledCapacity(
        long siteCount,
        long deviceCount,
        BigDecimal solarPowerKw,
        BigDecimal batteryPowerKw,
        BigDecimal evChargerPowerKw
) {
    public VppInstalledCapacity {
        if (siteCount < 0 || deviceCount < 0) {
            throw new IllegalArgumentException("Capacity counts cannot be negative");
        }
        requireNonNegative(solarPowerKw, "solarPowerKw");
        requireNonNegative(batteryPowerKw, "batteryPowerKw");
        requireNonNegative(evChargerPowerKw, "evChargerPowerKw");
    }

    public BigDecimal totalRatedPowerKw() {
        return solarPowerKw.add(batteryPowerKw).add(evChargerPowerKw);
    }

    private static void requireNonNegative(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }
}
