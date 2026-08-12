package io.voltweave.intelligence.forecast.application.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;

public record Forecast(
        UUID id,
        UUID organizationId,
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
        List<ForecastPoint> points
) {
    public Forecast {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        Objects.requireNonNull(horizon, "horizon is required");
        points = List.copyOf(points);
    }
}
