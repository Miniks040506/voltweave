package io.voltweave.settlement.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Settlement(
        UUID id,
        UUID organizationId,
        UUID dispatchId,
        UUID vppId,
        String completionStatus,
        BigDecimal targetPowerKw,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Instant baselineFrozenAt,
        UUID baselineId,
        long baselineVersion,
        String baselineModelName,
        String baselineModelVersion,
        BigDecimal expectedEnergyKwh,
        BigDecimal deliveredEnergyKwh,
        BigDecimal achievementPercent,
        Instant calculatedAt,
        List<BaselinePoint> baselinePoints,
        List<Line> lines
) {
    public record BaselinePoint(Instant forecastAt, BigDecimal baselineGridImportKw) {
    }

    public record Line(
            UUID siteId,
            UUID participantId,
            String participantType,
            BigDecimal requestedPowerKw,
            BigDecimal expectedEnergyKwh,
            BigDecimal deliveredEnergyKwh,
            BigDecimal achievementPercent
    ) {
    }
}
