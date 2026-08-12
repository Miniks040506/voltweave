package io.voltweave.intelligence.forecast.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record TrainingSample(
        Instant observedAt,
        BigDecimal gridImportKw,
        BigDecimal solarGenerationKw
) {
    public TrainingSample {
        Objects.requireNonNull(observedAt, "observedAt is required");
        Objects.requireNonNull(gridImportKw, "gridImportKw is required");
        Objects.requireNonNull(solarGenerationKw, "solarGenerationKw is required");
        if (solarGenerationKw.signum() < 0) {
            throw new IllegalArgumentException("solarGenerationKw must not be negative");
        }
    }
}
