package io.voltweave.settlement.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.settlement.application.model.Settlement;

public record SettlementResponse(
        UUID id,
        UUID dispatchId,
        UUID vppId,
        String completionStatus,
        BigDecimal targetPowerKw,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        UUID baselineId,
        long baselineVersion,
        String baselineModelName,
        String baselineModelVersion,
        BigDecimal expectedEnergyKwh,
        BigDecimal deliveredEnergyKwh,
        BigDecimal achievementPercent,
        String status,
        Instant calculatedAt,
        List<BaselinePoint> baselinePoints,
        List<Line> lines
) {
    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.id(), settlement.dispatchId(), settlement.vppId(),
                settlement.completionStatus(), settlement.targetPowerKw(),
                settlement.scheduledStartAt(), settlement.scheduledEndAt(),
                settlement.baselineId(), settlement.baselineVersion(),
                settlement.baselineModelName(), settlement.baselineModelVersion(),
                settlement.expectedEnergyKwh(), settlement.deliveredEnergyKwh(),
                settlement.achievementPercent(), settlement.status(), settlement.calculatedAt(),
                settlement.baselinePoints().stream().map(BaselinePoint::from).toList(),
                settlement.lines().stream().map(Line::from).toList()
        );
    }

    public record BaselinePoint(Instant forecastAt, BigDecimal baselineGridImportKw) {
        private static BaselinePoint from(Settlement.BaselinePoint point) {
            return new BaselinePoint(point.forecastAt(), point.baselineGridImportKw());
        }
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
        private static Line from(Settlement.Line line) {
            return new Line(
                    line.siteId(), line.participantId(), line.participantType(),
                    line.requestedPowerKw(), line.expectedEnergyKwh(),
                    line.deliveredEnergyKwh(), line.achievementPercent()
            );
        }
    }
}
