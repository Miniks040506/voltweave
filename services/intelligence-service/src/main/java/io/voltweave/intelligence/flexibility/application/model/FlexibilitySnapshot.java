package io.voltweave.intelligence.flexibility.application.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FlexibilitySnapshot(
        UUID id,
        UUID organizationId,
        UUID vppId,
        long version,
        Duration dispatchDuration,
        Instant generatedAt,
        Instant validUntil,
        BigDecimal upwardFlexibilityKw,
        BigDecimal availableEnergyKwh,
        List<FlexibilityCandidate> candidates
) {
}
