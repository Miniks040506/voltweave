package io.voltweave.intelligence.forecast.domain.entities;

import java.math.BigDecimal;
import java.util.Objects;

public record ForecastValue(
        BigDecimal baselineGridImportKw,
        BigDecimal solarGenerationKw
) {
    public ForecastValue {
        Objects.requireNonNull(baselineGridImportKw, "baselineGridImportKw is required");
        Objects.requireNonNull(solarGenerationKw, "solarGenerationKw is required");
        if (solarGenerationKw.signum() < 0) {
            throw new IllegalArgumentException("solarGenerationKw must not be negative");
        }
    }
}
