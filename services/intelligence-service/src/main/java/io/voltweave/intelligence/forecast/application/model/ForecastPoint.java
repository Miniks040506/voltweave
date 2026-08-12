package io.voltweave.intelligence.forecast.application.model;

import java.math.BigDecimal;
import java.time.Instant;

public record ForecastPoint(
        Instant forecastAt,
        BigDecimal baselineGridImportKw,
        BigDecimal solarGenerationKw
) {
}
