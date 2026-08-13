package io.voltweave.dispatch.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispatchPerformance(
        UUID dispatchId,
        BigDecimal requestedPowerKw,
        BigDecimal deliveredPowerKw,
        BigDecimal errorKw,
        BigDecimal achievementPercent,
        BigDecimal deliveredEnergyKwh,
        List<Point> points
) {
    public record Point(
            UUID deviceId,
            Instant observedAt,
            BigDecimal targetPowerKw,
            BigDecimal requestedPowerKw,
            BigDecimal actualPowerKw,
            BigDecimal deliveredPowerKw,
            BigDecimal errorKw,
            BigDecimal errorPercent,
            BigDecimal cumulativeDeliveredEnergyKwh,
            boolean online
    ) {
    }
}

