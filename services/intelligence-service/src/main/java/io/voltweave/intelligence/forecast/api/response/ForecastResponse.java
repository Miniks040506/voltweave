package io.voltweave.intelligence.forecast.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.intelligence.forecast.application.model.Forecast;
import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;

public record ForecastResponse(
        UUID id,
        UUID vppId,
        long version,
        ForecastHorizon horizon,
        String modelName,
        String modelVersion,
        Instant generatedAt,
        Instant trainingFrom,
        Instant trainingTo,
        Instant targetStart,
        Instant targetEnd,
        Instant validUntil,
        List<ForecastPointResponse> points
) {
    public static ForecastResponse from(Forecast forecast) {
        return new ForecastResponse(
                forecast.id(), forecast.vppId(), forecast.version(), forecast.horizon(),
                forecast.modelName(), forecast.modelVersion(), forecast.generatedAt(),
                forecast.trainingFrom(), forecast.trainingTo(), forecast.targetStart(),
                forecast.targetEnd(), forecast.validUntil(), forecast.points().stream()
                        .map(ForecastPointResponse::from).toList()
        );
    }
}
