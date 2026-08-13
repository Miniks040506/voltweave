package io.voltweave.intelligence.optimization.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OptimizationCandidate(
        UUID siteId,
        UUID deviceId,
        String deviceType,
        BigDecimal availablePowerKw,
        BigDecimal availableEnergyKwh,
        BigDecimal reliability,
        BigDecimal availableSoc,
        BigDecimal responseSpeed,
        BigDecimal lowDegradationCost,
        BigDecimal customerPreference,
        BigDecimal score,
        BigDecimal allocatedPowerKw,
        boolean eligible
) {
}
