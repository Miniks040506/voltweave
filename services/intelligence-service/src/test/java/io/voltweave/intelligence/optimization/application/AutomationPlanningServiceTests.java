package io.voltweave.intelligence.optimization.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.voltweave.intelligence.flexibility.application.FlexibilitySnapshotApplicationService;
import io.voltweave.intelligence.forecast.application.model.Forecast;
import io.voltweave.intelligence.forecast.application.model.ForecastPoint;
import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;
import io.voltweave.intelligence.forecast.persistence.ForecastRepository;
import io.voltweave.intelligence.optimization.application.model.OptimizationPreview;

class AutomationPlanningServiceTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-13T03:01:00Z");
    private ForecastRepository forecasts;
    private FlexibilitySnapshotApplicationService flexibility;
    private OptimizationApplicationService optimization;
    private SimulatedTariffSchedule tariff;
    private AutomationPlanningService service;

    @BeforeEach
    void setUp() {
        forecasts = mock(ForecastRepository.class);
        flexibility = mock(FlexibilitySnapshotApplicationService.class);
        optimization = mock(OptimizationApplicationService.class);
        tariff = mock(SimulatedTariffSchedule.class);
        service = new AutomationPlanningService(
                forecasts, flexibility, optimization, tariff,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsCappedPlanForConsecutivePeakIntervals() {
        when(forecasts.latest(ORGANIZATION_ID, VPP_ID)).thenReturn(Optional.of(forecast(
                new BigDecimal("12"), new BigDecimal("15"), new BigDecimal("9")
        )));
        var preview = mock(OptimizationPreview.class);
        when(optimization.generate(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("3"), new BigDecimal("10")
        )).thenReturn(preview);

        var plan = service.peakLimitPlan(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("10"), new BigDecimal("3"),
                60, new BigDecimal("10")
        ).orElseThrow();

        assertEquals(Instant.parse("2026-08-13T03:15:00Z"), plan.scheduledStartAt());
        assertEquals(Duration.ofMinutes(30), plan.duration());
        assertEquals(preview, plan.preview());
        verify(flexibility).generate(ORGANIZATION_ID, VPP_ID, Duration.ofMinutes(30));
    }

    @Test
    void doesNotOptimizeWhenForecastDoesNotCrossLimit() {
        when(forecasts.latest(ORGANIZATION_ID, VPP_ID)).thenReturn(Optional.of(forecast(
                new BigDecimal("8"), new BigDecimal("10"), new BigDecimal("9")
        )));

        assertTrue(service.peakLimitPlan(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("10"), new BigDecimal("3"),
                30, BigDecimal.ZERO
        ).isEmpty());
        verify(flexibility, never()).generate(ORGANIZATION_ID, VPP_ID, Duration.ofMinutes(30));
    }

    @Test
    void createsPricePlanOnlyForIntervalsAboveThreshold() {
        var forecast = forecast(
                new BigDecimal("4"), new BigDecimal("3"), new BigDecimal("2")
        );
        when(forecasts.latest(ORGANIZATION_ID, VPP_ID)).thenReturn(Optional.of(forecast));
        when(tariff.priceAt(forecast.points().get(0).forecastAt()))
                .thenReturn(new BigDecimal("0.30"));
        when(tariff.priceAt(forecast.points().get(1).forecastAt()))
                .thenReturn(new BigDecimal("0.30"));
        when(tariff.priceAt(forecast.points().get(2).forecastAt()))
                .thenReturn(new BigDecimal("0.10"));
        var preview = mock(OptimizationPreview.class);
        when(optimization.generate(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("3"), BigDecimal.ZERO
        )).thenReturn(preview);

        var plan = service.priceThresholdPlan(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("0.20"),
                new BigDecimal("5"), 60, BigDecimal.ZERO
        ).orElseThrow();

        assertEquals(Duration.ofMinutes(30), plan.duration());
        assertEquals(preview, plan.preview());
    }

    private static Forecast forecast(BigDecimal... powers) {
        Instant start = Instant.parse("2026-08-13T03:15:00Z");
        var points = java.util.stream.IntStream.range(0, powers.length)
                .mapToObj(index -> new ForecastPoint(
                        start.plusSeconds(index * 900L), powers[index], BigDecimal.ZERO
                )).toList();
        return new Forecast(
                UUID.randomUUID(), ORGANIZATION_ID, VPP_ID, 1, ForecastHorizon.HOUR_1,
                "model", "1", NOW, NOW.minusSeconds(3600), NOW, start,
                start.plusSeconds(powers.length * 900L), NOW.plusSeconds(3600), points
        );
    }
}
