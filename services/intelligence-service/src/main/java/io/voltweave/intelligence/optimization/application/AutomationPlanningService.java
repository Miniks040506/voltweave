package io.voltweave.intelligence.optimization.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.voltweave.intelligence.flexibility.application.FlexibilitySnapshotApplicationService;
import io.voltweave.intelligence.forecast.application.model.Forecast;
import io.voltweave.intelligence.forecast.persistence.ForecastRepository;
import io.voltweave.intelligence.optimization.application.model.AutomationPlan;

@Service
public class AutomationPlanningService {
    private static final Duration INTERVAL = Duration.ofMinutes(15);

    private final ForecastRepository forecastRepository;
    private final FlexibilitySnapshotApplicationService flexibilityService;
    private final OptimizationApplicationService optimizationService;
    private final Clock clock;

    @Autowired
    public AutomationPlanningService(
            ForecastRepository forecastRepository,
            FlexibilitySnapshotApplicationService flexibilityService,
            OptimizationApplicationService optimizationService
    ) {
        this(forecastRepository, flexibilityService, optimizationService, Clock.systemUTC());
    }

    AutomationPlanningService(
            ForecastRepository forecastRepository,
            FlexibilitySnapshotApplicationService flexibilityService,
            OptimizationApplicationService optimizationService,
            Clock clock
    ) {
        this.forecastRepository = forecastRepository;
        this.flexibilityService = flexibilityService;
        this.optimizationService = optimizationService;
        this.clock = clock;
    }

    public Optional<AutomationPlan> peakLimitPlan(
            UUID organizationId,
            UUID vppId,
            BigDecimal peakImportLimitKw,
            BigDecimal maxDispatchPowerKw,
            int maxDispatchDurationMinutes,
            BigDecimal reserveMarginPercent
    ) {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        requirePositive(peakImportLimitKw, "peakImportLimitKw");
        requirePositive(maxDispatchPowerKw, "maxDispatchPowerKw");
        if (reserveMarginPercent == null || reserveMarginPercent.signum() < 0
                || reserveMarginPercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("reserveMarginPercent must be between 0 and 100");
        }
        int maxIntervals = maxDispatchDurationMinutes / 15;
        if (maxIntervals < 1) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        Forecast forecast = forecastRepository.latest(organizationId, vppId)
                .filter(value -> value.validUntil().isAfter(now))
                .orElseThrow(() -> new IllegalStateException(
                        "A valid forecast is required for automation"
                ));
        int startIndex = -1;
        for (int index = 0; index < forecast.points().size(); index++) {
            var point = forecast.points().get(index);
            if (point.forecastAt().isAfter(now)
                    && point.forecastAt().getEpochSecond() % INTERVAL.toSeconds() == 0
                    && point.baselineGridImportKw().compareTo(peakImportLimitKw) > 0) {
                startIndex = index;
                break;
            }
        }
        if (startIndex < 0) {
            return Optional.empty();
        }

        int intervals = consecutivePeakIntervals(
                forecast, startIndex, maxIntervals, peakImportLimitKw
        );
        var selected = forecast.points().subList(startIndex, startIndex + intervals);
        BigDecimal targetPowerKw = selected.stream()
                .map(point -> point.baselineGridImportKw().subtract(peakImportLimitKw))
                .max(BigDecimal::compareTo).orElseThrow()
                .min(maxDispatchPowerKw);
        Duration duration = INTERVAL.multipliedBy(intervals);
        flexibilityService.generate(organizationId, vppId, duration);
        var preview = optimizationService.generate(
                organizationId, vppId, targetPowerKw, reserveMarginPercent
        );
        return Optional.of(new AutomationPlan(
                selected.getFirst().forecastAt(), duration, preview
        ));
    }

    private static int consecutivePeakIntervals(
            Forecast forecast,
            int startIndex,
            int maxIntervals,
            BigDecimal limit
    ) {
        int count = 1;
        Instant expected = forecast.points().get(startIndex).forecastAt().plus(INTERVAL);
        while (count < maxIntervals && startIndex + count < forecast.points().size()) {
            var next = forecast.points().get(startIndex + count);
            if (!next.forecastAt().equals(expected)
                    || next.baselineGridImportKw().compareTo(limit) <= 0) {
                break;
            }
            count++;
            expected = expected.plus(INTERVAL);
        }
        return count;
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
