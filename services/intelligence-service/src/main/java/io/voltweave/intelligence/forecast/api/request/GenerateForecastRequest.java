package io.voltweave.intelligence.forecast.api.request;

import java.time.Instant;

import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;
import jakarta.validation.constraints.NotNull;

public record GenerateForecastRequest(
        @NotNull ForecastHorizon horizon,
        @NotNull Instant targetStart
) {
}
