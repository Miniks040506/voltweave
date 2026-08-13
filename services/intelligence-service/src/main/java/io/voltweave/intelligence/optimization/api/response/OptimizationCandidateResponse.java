package io.voltweave.intelligence.optimization.api.response;

import java.math.BigDecimal;
import java.util.UUID;

import io.voltweave.intelligence.optimization.application.model.OptimizationCandidate;

public record OptimizationCandidateResponse(
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
    static OptimizationCandidateResponse from(OptimizationCandidate candidate) {
        return new OptimizationCandidateResponse(
                candidate.siteId(), candidate.deviceId(), candidate.deviceType(),
                candidate.availablePowerKw(), candidate.availableEnergyKwh(),
                candidate.reliability(), candidate.availableSoc(), candidate.responseSpeed(),
                candidate.lowDegradationCost(), candidate.customerPreference(), candidate.score(),
                candidate.allocatedPowerKw(), candidate.eligible()
        );
    }
}
