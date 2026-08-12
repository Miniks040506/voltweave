package io.voltweave.intelligence.forecast.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.voltweave.intelligence.forecast.domain.entities.TrainingSample;
import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;

class SameTimeWeightedAverageTests {
    @Test
    void givesMoreWeightToRecentSameTimeSamples() {
        var result = SameTimeWeightedAverage.predict(List.of(
                sample("2026-08-09T12:00:00Z", "9", "3"),
                sample("2026-08-10T12:00:00Z", "12", "6"),
                sample("2026-08-11T12:00:00Z", "18", "9")
        ));

        assertEquals(0, result.baselineGridImportKw().compareTo(new BigDecimal("14.500")));
        assertEquals(0, result.solarGenerationKw().compareTo(new BigDecimal("7.000")));
    }

    @Test
    void orderingInputDoesNotChangeForecast() {
        var older = sample("2026-08-10T12:00:00Z", "10", "2");
        var newer = sample("2026-08-11T12:00:00Z", "16", "5");

        assertEquals(
                SameTimeWeightedAverage.predict(List.of(newer, older)),
                SameTimeWeightedAverage.predict(List.of(older, newer))
        );
    }

    @Test
    void rejectsMissingTrainingData() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> SameTimeWeightedAverage.predict(List.of())
        );
        assertEquals("at least one training sample is required", exception.getMessage());
    }

    @Test
    void exposesRequiredFifteenMinuteHorizons() {
        assertEquals(1, ForecastHorizon.MINUTES_15.pointCount());
        assertEquals(4, ForecastHorizon.HOUR_1.pointCount());
        assertEquals(96, ForecastHorizon.DAY_AHEAD.pointCount());
    }

    private static TrainingSample sample(String observedAt, String load, String solar) {
        return new TrainingSample(
                Instant.parse(observedAt), new BigDecimal(load), new BigDecimal(solar)
        );
    }
}
