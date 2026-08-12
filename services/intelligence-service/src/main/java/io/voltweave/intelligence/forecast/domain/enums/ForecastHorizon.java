package io.voltweave.intelligence.forecast.domain.enums;

import java.time.Duration;

public enum ForecastHorizon {
    MINUTES_15(Duration.ofMinutes(15)),
    HOUR_1(Duration.ofHours(1)),
    DAY_AHEAD(Duration.ofDays(1));

    public static final Duration INTERVAL = Duration.ofMinutes(15);

    private final Duration duration;

    ForecastHorizon(Duration duration) {
        this.duration = duration;
    }

    public Duration duration() {
        return duration;
    }

    public int pointCount() {
        return Math.toIntExact(duration.dividedBy(INTERVAL));
    }
}
