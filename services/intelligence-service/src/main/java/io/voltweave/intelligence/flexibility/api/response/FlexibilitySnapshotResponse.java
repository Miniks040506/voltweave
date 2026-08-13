package io.voltweave.intelligence.flexibility.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.intelligence.flexibility.application.model.FlexibilitySnapshot;

public record FlexibilitySnapshotResponse(
        UUID id,
        UUID vppId,
        long version,
        long dispatchDurationSeconds,
        Instant generatedAt,
        Instant validUntil,
        BigDecimal upwardFlexibilityKw,
        BigDecimal availableEnergyKwh,
        List<FlexibilityCandidateResponse> candidates
) {
    public static FlexibilitySnapshotResponse from(FlexibilitySnapshot snapshot) {
        return new FlexibilitySnapshotResponse(
                snapshot.id(), snapshot.vppId(), snapshot.version(),
                snapshot.dispatchDuration().toSeconds(), snapshot.generatedAt(),
                snapshot.validUntil(), snapshot.upwardFlexibilityKw(),
                snapshot.availableEnergyKwh(), snapshot.candidates().stream()
                        .map(FlexibilityCandidateResponse::from).toList()
        );
    }
}
