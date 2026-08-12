package io.voltweave.intelligence.forecast.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.intelligence.forecast.domain.entities.TrainingSample;
import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;
import io.voltweave.intelligence.forecast.persistence.ForecastRepository;

class ForecastApplicationServiceTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000019"
    );
    private static final UUID VPP_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000019"
    );
    private static final Instant NOW = Instant.parse("2026-08-13T00:07:00Z");

    private final ForecastRepository repository = mock(ForecastRepository.class);
    private final ForecastApplicationService service = new ForecastApplicationService(
            repository, Duration.ofDays(7), Duration.ofMinutes(30),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void generatesEveryIntervalAsOneImmutableVersion() {
        when(repository.trainingSamples(
                eq(ORGANIZATION_ID), eq(VPP_ID), any(), any(), eq(NOW)
        )).thenReturn(List.of(
                sample("2026-08-11T00:15:00Z", "10", "2"),
                sample("2026-08-12T00:15:00Z", "16", "4")
        ));
        when(repository.nextVersion(ORGANIZATION_ID, VPP_ID, NOW)).thenReturn(3L);

        var result = service.generate(
                ORGANIZATION_ID, VPP_ID, ForecastHorizon.HOUR_1,
                Instant.parse("2026-08-13T00:15:00Z")
        );

        assertEquals(3L, result.version());
        assertEquals(4, result.points().size());
        assertEquals(0, result.points().getFirst().baselineGridImportKw()
                .compareTo(new BigDecimal("10.667")));
        assertEquals(NOW.plus(Duration.ofMinutes(30)), result.validUntil());
        verify(repository).insert(result);
    }

    @Test
    void failsClosedWhenAnyIntervalHasNoTrainingData() {
        when(repository.trainingSamples(any(), any(), any(), any(), any()))
                .thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> service.generate(
                ORGANIZATION_ID, VPP_ID, ForecastHorizon.MINUTES_15,
                Instant.parse("2026-08-13T00:15:00Z")
        ));
    }

    @Test
    void rejectsPastOrUnalignedTargets() {
        assertThrows(IllegalArgumentException.class, () -> service.generate(
                ORGANIZATION_ID, VPP_ID, ForecastHorizon.HOUR_1,
                Instant.parse("2026-08-13T00:00:00Z")
        ));
        assertThrows(IllegalArgumentException.class, () -> service.generate(
                ORGANIZATION_ID, VPP_ID, ForecastHorizon.HOUR_1,
                Instant.parse("2026-08-13T00:16:00Z")
        ));
    }

    private static TrainingSample sample(String at, String load, String solar) {
        return new TrainingSample(
                Instant.parse(at), new BigDecimal(load), new BigDecimal(solar)
        );
    }
}
