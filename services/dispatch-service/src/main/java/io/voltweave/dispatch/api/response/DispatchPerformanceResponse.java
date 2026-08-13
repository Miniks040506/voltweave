package io.voltweave.dispatch.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.dispatch.application.model.DispatchPerformance;

public record DispatchPerformanceResponse(
        UUID dispatchId,
        BigDecimal requestedPowerKw,
        BigDecimal deliveredPowerKw,
        BigDecimal errorKw,
        BigDecimal achievementPercent,
        BigDecimal deliveredEnergyKwh,
        List<Point> points
) {
    public static DispatchPerformanceResponse from(DispatchPerformance performance) {
        return new DispatchPerformanceResponse(
                performance.dispatchId(), performance.requestedPowerKw(),
                performance.deliveredPowerKw(), performance.errorKw(),
                performance.achievementPercent(), performance.deliveredEnergyKwh(),
                performance.points().stream().map(Point::from).toList()
        );
    }

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
        private static Point from(DispatchPerformance.Point point) {
            return new Point(
                    point.deviceId(), point.observedAt(), point.targetPowerKw(),
                    point.requestedPowerKw(), point.actualPowerKw(),
                    point.deliveredPowerKw(), point.errorKw(), point.errorPercent(),
                    point.cumulativeDeliveredEnergyKwh(), point.online()
            );
        }
    }
}
