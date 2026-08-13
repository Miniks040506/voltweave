package io.voltweave.intelligence.flexibility.api.response;

import java.math.BigDecimal;
import java.util.UUID;

import io.voltweave.intelligence.flexibility.application.model.FlexibilityCandidate;

public record FlexibilityCandidateResponse(
        UUID siteId,
        UUID deviceId,
        String deviceType,
        BigDecimal rawUpwardFlexibilityKw,
        BigDecimal upwardFlexibilityKw,
        BigDecimal availableEnergyKwh,
        String limitingReason
) {
    static FlexibilityCandidateResponse from(FlexibilityCandidate candidate) {
        return new FlexibilityCandidateResponse(
                candidate.siteId(), candidate.deviceId(), candidate.deviceType(),
                candidate.rawUpwardFlexibilityKw(), candidate.upwardFlexibilityKw(),
                candidate.availableEnergyKwh(), candidate.limitingReason()
        );
    }
}
