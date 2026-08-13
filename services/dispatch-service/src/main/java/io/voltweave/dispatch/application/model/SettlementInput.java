package io.voltweave.dispatch.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SettlementInput(
        UUID organizationId,
        UUID dispatchId,
        UUID vppId,
        String completionStatus,
        BigDecimal targetPowerKw,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Instant frozenAt,
        UUID baselineId,
        long baselineVersion,
        String baselineModelName,
        String baselineModelVersion,
        List<BaselinePoint> baselinePoints,
        List<Participant> participants
) {
    public record BaselinePoint(Instant forecastAt, BigDecimal baselineGridImportKw) {
    }

    public record Participant(
            UUID siteId,
            UUID deviceId,
            String deviceType,
            BigDecimal requestedPowerKw,
            BigDecimal expectedEnergyKwh,
            BigDecimal deliveredEnergyKwh
    ) {
    }
}
