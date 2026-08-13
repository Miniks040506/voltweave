package io.voltweave.dispatch.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.dispatch.domain.enums.DispatchStatus;

public record Dispatch(
        UUID id,
        UUID organizationId,
        UUID vppId,
        UUID optimizationPreviewId,
        long optimizationPreviewVersion,
        String type,
        BigDecimal targetPowerKw,
        BigDecimal requiredPowerKw,
        BigDecimal plannedPowerKw,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        DispatchStatus status,
        String createdBy,
        Instant createdAt,
        long version,
        Baseline baseline,
        List<Allocation> allocations
) {
    public record Allocation(
            UUID siteId,
            UUID deviceId,
            String deviceType,
            BigDecimal sourceAvailablePowerKw,
            BigDecimal allocatedPowerKw,
            BigDecimal expectedEnergyKwh,
            BigDecimal score
    ) {
    }

    public record Baseline(
            UUID forecastId,
            long forecastVersion,
            String modelName,
            String modelVersion,
            Instant sourceValidUntil,
            Instant frozenAt,
            List<BaselinePoint> points
    ) {
    }

    public record BaselinePoint(Instant forecastAt, BigDecimal baselineGridImportKw) {
    }
}
