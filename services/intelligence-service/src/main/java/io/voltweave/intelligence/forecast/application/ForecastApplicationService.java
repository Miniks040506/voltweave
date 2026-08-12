package io.voltweave.intelligence.forecast.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.intelligence.forecast.application.model.Forecast;
import io.voltweave.intelligence.forecast.application.model.ForecastPoint;
import io.voltweave.intelligence.forecast.domain.SameTimeWeightedAverage;
import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;
import io.voltweave.intelligence.forecast.persistence.ForecastRepository;

@Service
public class ForecastApplicationService {
    private final ForecastRepository repository;
    private final Duration trainingWindow;
    private final Duration freshness;
    private final Clock clock;

    @Autowired
    public ForecastApplicationService(
            ForecastRepository repository,
            @Value("${voltweave.forecast.training-window:7d}") Duration trainingWindow,
            @Value("${voltweave.forecast.freshness:30m}") Duration freshness
    ) {
        this(repository, trainingWindow, freshness, Clock.systemUTC());
    }

    ForecastApplicationService(
            ForecastRepository repository,
            Duration trainingWindow,
            Duration freshness,
            Clock clock
    ) {
        if (trainingWindow.isZero() || trainingWindow.isNegative()) {
            throw new IllegalArgumentException("training-window must be positive");
        }
        if (freshness.isZero() || freshness.isNegative()) {
            throw new IllegalArgumentException("freshness must be positive");
        }
        this.repository = repository;
        this.trainingWindow = trainingWindow;
        this.freshness = freshness;
        this.clock = clock;
    }

    @Transactional
    public Forecast generate(
            UUID organizationId,
            UUID vppId,
            ForecastHorizon horizon,
            Instant targetStart
    ) {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        Objects.requireNonNull(horizon, "horizon is required");
        var generatedAt = clock.instant();
        validateTarget(targetStart, generatedAt);
        var trainingFrom = generatedAt.minus(trainingWindow);
        var points = new ArrayList<ForecastPoint>(horizon.pointCount());

        for (int index = 0; index < horizon.pointCount(); index++) {
            var forecastAt = targetStart.plus(
                    ForecastHorizon.INTERVAL.multipliedBy(index)
            );
            var samples = repository.trainingSamples(
                    organizationId, vppId, forecastAt, trainingFrom, generatedAt
            );
            if (samples.isEmpty()) {
                throw new IllegalStateException(
                        "No training data for forecast interval " + forecastAt
                );
            }
            var value = SameTimeWeightedAverage.predict(samples);
            points.add(new ForecastPoint(
                    forecastAt,
                    value.baselineGridImportKw(),
                    value.solarGenerationKw()
            ));
        }

        var forecast = new Forecast(
                UUID.randomUUID(), organizationId, vppId,
                repository.nextVersion(organizationId, vppId, generatedAt),
                horizon, SameTimeWeightedAverage.MODEL_NAME,
                SameTimeWeightedAverage.MODEL_VERSION, generatedAt,
                trainingFrom, generatedAt, targetStart,
                targetStart.plus(horizon.duration()), generatedAt.plus(freshness), points
        );
        repository.insert(forecast);
        return forecast;
    }

    @Transactional(readOnly = true)
    public Optional<Forecast> latest(UUID organizationId, UUID vppId) {
        return repository.latest(organizationId, vppId);
    }

    private static void validateTarget(Instant targetStart, Instant generatedAt) {
        if (targetStart == null || targetStart.isBefore(generatedAt)) {
            throw new IllegalArgumentException("targetStart must not be in the past");
        }
        var utc = targetStart.atZone(ZoneOffset.UTC);
        if (utc.getMinute() % 15 != 0
                || !targetStart.equals(targetStart.truncatedTo(ChronoUnit.MINUTES))) {
            throw new IllegalArgumentException("targetStart must align to 15 minutes");
        }
    }
}
