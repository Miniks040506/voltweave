package io.voltweave.intelligence.optimization.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.intelligence.optimization.application.model.OptimizationPreview;

public record OptimizationPreviewResponse(
        UUID id,
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
        List<OptimizationCandidateResponse> candidates
) {
    public static OptimizationPreviewResponse from(OptimizationPreview preview) {
        return new OptimizationPreviewResponse(
                preview.id(), preview.vppId(), preview.version(),
                preview.flexibilitySnapshotId(), preview.flexibilitySnapshotVersion(),
                preview.targetPowerKw(), preview.reserveMarginPercent(),
                preview.requiredPowerKw(), preview.plannedPowerKw(), preview.feasible(),
                preview.weightVersion(), preview.createdAt(), preview.candidates().stream()
                        .map(OptimizationCandidateResponse::from).toList()
        );
    }
}
