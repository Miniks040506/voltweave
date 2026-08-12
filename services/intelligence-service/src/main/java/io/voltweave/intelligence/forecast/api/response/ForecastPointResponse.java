package io.voltweave.intelligence.forecast.api.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.voltweave.intelligence.forecast.application.model.ForecastPoint;

public record ForecastPointResponse(
        Instant forecastAt,
        BigDecimal baselineGridImportKw,
        BigDecimal solarGenerationKw
) {
    static ForecastPointResponse from(ForecastPoint point) {
        return new ForecastPointResponse(
                point.forecastAt(), point.baselineGridImportKw(),
                point.solarGenerationKw()
        );
    }
}
