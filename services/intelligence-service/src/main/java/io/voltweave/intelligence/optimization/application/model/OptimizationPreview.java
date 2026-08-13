package io.voltweave.intelligence.optimization.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OptimizationPreview(
        UUID id,
        UUID organizationId,
        UUID vppId,
        long version,
        UUID flexibilitySnapshotId,
        long flexibilitySnapshotVersion,
        BigDecimal targetPowerKw,
        BigDecimal reserveMarginPercent,
        BigDecimal requiredPowerKw,
        BigDecimal plannedPowerKw,
        boolean feasible,
        String weightVersion,
        Instant createdAt,
        List<OptimizationCandidate> candidates
) {
}
