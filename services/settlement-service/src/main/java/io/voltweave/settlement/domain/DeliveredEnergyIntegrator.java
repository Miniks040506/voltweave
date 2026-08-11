package io.voltweave.settlement.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public final class DeliveredEnergyIntegrator {

    private DeliveredEnergyIntegrator() {
    }

    public static double integrateKwh(List<Interval> intervals) {
        Objects.requireNonNull(intervals, "intervals must not be null");

        double deliveredEnergyKwh = 0.0;
        for (var interval : intervals) {
            Objects.requireNonNull(interval, "interval must not be null");

            double deliveredReductionKw = Math.max(
                    0.0,
                    interval.baselineGridImportKw() - interval.actualGridImportKw());
            double intervalSeconds = interval.duration().getSeconds()
                    + interval.duration().getNano() / 1_000_000_000.0;
            double intervalEnergyKwh = deliveredReductionKw * intervalSeconds / 3_600.0;

            if (!Double.isFinite(intervalEnergyKwh + deliveredEnergyKwh)) {
                throw new IllegalArgumentException("integrated energy must remain finite");
            }
            deliveredEnergyKwh += intervalEnergyKwh;
        }

        return deliveredEnergyKwh;
    }

    public record Interval(
            double baselineGridImportKw,
            double actualGridImportKw,
            Duration duration) {

        public Interval {
            requireFinite("baselineGridImportKw", baselineGridImportKw);
            requireFinite("actualGridImportKw", actualGridImportKw);
            if (duration == null || duration.isZero() || duration.isNegative()) {
                throw new IllegalArgumentException("duration must be positive");
            }
        }
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
