package io.voltweave.intelligence.optimization.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatchInput(
        UUID optimizationPreviewId,
        long optimizationPreviewVersion,
        UUID organizationId,
        UUID vppId,
        long dispatchDurationSeconds,
        BigDecimal targetPowerKw,
        BigDecimal requiredPowerKw,
        BigDecimal plannedPowerKw,
        boolean feasible,
        UUID forecastId,
        long forecastVersion,
        String forecastModelName,
        String forecastModelVersion,
        Instant forecastValidUntil,
        List<OptimizationCandidate> allocations,
        List<BaselinePoint> baselinePoints
) {
    public record BaselinePoint(Instant forecastAt, BigDecimal baselineGridImportKw) {
    }
}
