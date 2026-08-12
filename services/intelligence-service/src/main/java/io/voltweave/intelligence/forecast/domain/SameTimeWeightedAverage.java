package io.voltweave.intelligence.forecast.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import io.voltweave.intelligence.forecast.domain.entities.ForecastValue;
import io.voltweave.intelligence.forecast.domain.entities.TrainingSample;

public final class SameTimeWeightedAverage {
    public static final String MODEL_NAME = "same-time-weighted-average";
    public static final String MODEL_VERSION = "1.0";
    private static final int SCALE = 3;

    private SameTimeWeightedAverage() {
    }

    public static ForecastValue predict(List<TrainingSample> samples) {
        Objects.requireNonNull(samples, "samples must not be null");
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("at least one training sample is required");
        }

        var ordered = samples.stream()
                .map(sample -> Objects.requireNonNull(sample, "sample must not be null"))
                .sorted(Comparator.comparing(TrainingSample::observedAt))
                .toList();
        BigDecimal loadTotal = BigDecimal.ZERO;
        BigDecimal solarTotal = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;

        for (int index = 0; index < ordered.size(); index++) {
            var sample = ordered.get(index);
            var weight = BigDecimal.valueOf(index + 1L);
            loadTotal = loadTotal.add(sample.gridImportKw().multiply(weight));
            solarTotal = solarTotal.add(sample.solarGenerationKw().multiply(weight));
            weightTotal = weightTotal.add(weight);
        }

        return new ForecastValue(
                loadTotal.divide(weightTotal, SCALE, RoundingMode.HALF_UP),
                solarTotal.divide(weightTotal, SCALE, RoundingMode.HALF_UP)
        );
    }
}
