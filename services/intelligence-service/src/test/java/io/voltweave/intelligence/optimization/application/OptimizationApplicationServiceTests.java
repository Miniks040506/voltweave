package io.voltweave.intelligence.optimization.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.voltweave.intelligence.domain.WeightedAllocator.Weights;
import io.voltweave.intelligence.flexibility.application.model.FlexibilityCandidate;
import io.voltweave.intelligence.flexibility.application.model.FlexibilitySnapshot;
import io.voltweave.intelligence.flexibility.persistence.FlexibilityRepository;
import io.voltweave.intelligence.forecast.application.model.Forecast;
import io.voltweave.intelligence.forecast.application.model.ForecastPoint;
import io.voltweave.intelligence.forecast.domain.enums.ForecastHorizon;
import io.voltweave.intelligence.forecast.persistence.ForecastRepository;
import io.voltweave.intelligence.optimization.application.model.OptimizationCandidate;
import io.voltweave.intelligence.optimization.application.model.OptimizationPreview;
import io.voltweave.intelligence.optimization.persistence.OptimizationRepository;

class OptimizationApplicationServiceTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-13T01:00:00Z");

    private FlexibilityRepository flexibilityRepository;
    private OptimizationRepository optimizationRepository;
    private ForecastRepository forecastRepository;
    private OptimizationApplicationService service;

    @BeforeEach
    void setUp() {
        flexibilityRepository = mock(FlexibilityRepository.class);
        optimizationRepository = mock(OptimizationRepository.class);
        forecastRepository = mock(ForecastRepository.class);
        service = new OptimizationApplicationService(
                flexibilityRepository, optimizationRepository, forecastRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(optimizationRepository.nextVersion(ORGANIZATION_ID, VPP_ID, NOW)).thenReturn(1L);
    }

    @Test
    void ranksEligibleCandidatesAndAllocatesReserveMargin() {
        when(flexibilityRepository.latest(ORGANIZATION_ID, VPP_ID))
                .thenReturn(Optional.of(snapshot(NOW.plusSeconds(60), List.of(
                        candidate("00000000-0000-0000-0000-000000000002", "BATTERY", "4", "4", null),
                        candidate("00000000-0000-0000-0000-000000000001", "EV_CHARGER", "4", "2", null),
                        candidate("00000000-0000-0000-0000-000000000003", "BATTERY", "0", "0", "SITE_OPTED_OUT")
                ))));

        OptimizationPreview result = service.generate(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("5"), new BigDecimal("10")
        );

        assertTrue(result.feasible());
        assertEquals(new BigDecimal("5.500"), result.requiredPowerKw());
        assertEquals(new BigDecimal("5.500"), result.plannedPowerKw());
        assertEquals("00000000-0000-0000-0000-000000000002",
                result.candidates().getFirst().deviceId().toString());
        assertEquals(new BigDecimal("4.000"), result.candidates().getFirst().allocatedPowerKw());
        assertFalse(result.candidates().getLast().eligible());

        var previewCaptor = ArgumentCaptor.forClass(OptimizationPreview.class);
        verify(optimizationRepository).insert(previewCaptor.capture(), any(Weights.class));
        assertEquals(result, previewCaptor.getValue());
    }

    @Test
    void reportsInfeasiblePlanWithoutHidingAvailableAllocation() {
        when(flexibilityRepository.latest(ORGANIZATION_ID, VPP_ID))
                .thenReturn(Optional.of(snapshot(NOW.plusSeconds(60), List.of(
                        candidate("00000000-0000-0000-0000-000000000001", "BATTERY", "2", "2", null)
                ))));

        var result = service.generate(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("5"), BigDecimal.ZERO
        );

        assertFalse(result.feasible());
        assertEquals(new BigDecimal("2.000"), result.plannedPowerKw());
    }

    @Test
    void excludesDevicesAlreadyAssignedToTheActiveDispatch() {
        UUID assigned = UUID.fromString("00000000-0000-0000-0000-000000000001");
        when(flexibilityRepository.latest(ORGANIZATION_ID, VPP_ID))
                .thenReturn(Optional.of(snapshot(NOW.plusSeconds(60), List.of(
                        candidate(assigned.toString(), "BATTERY", "5", "5", null),
                        candidate("00000000-0000-0000-0000-000000000002",
                                "BATTERY", "4", "4", null)
                ))));

        var result = service.generate(
                ORGANIZATION_ID, VPP_ID, new BigDecimal("3"), BigDecimal.ZERO,
                Set.of(assigned)
        );

        assertTrue(result.feasible());
        assertEquals(BigDecimal.ZERO.setScale(3), result.candidates().stream()
                .filter(candidate -> candidate.deviceId().equals(assigned))
                .findFirst().orElseThrow().allocatedPowerKw());
        assertEquals("00000000-0000-0000-0000-000000000002",
                result.candidates().getFirst().deviceId().toString());
    }

    @Test
    void rejectsMissingExpiredAndInvalidInputs() {
        when(flexibilityRepository.latest(ORGANIZATION_ID, VPP_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> service.generate(
                ORGANIZATION_ID, VPP_ID, BigDecimal.ONE, BigDecimal.ZERO
        ));

        when(flexibilityRepository.latest(ORGANIZATION_ID, VPP_ID))
                .thenReturn(Optional.of(snapshot(NOW, List.of())));
        assertThrows(IllegalStateException.class, () -> service.generate(
                ORGANIZATION_ID, VPP_ID, BigDecimal.ONE, BigDecimal.ZERO
        ));
        assertThrows(IllegalArgumentException.class, () -> service.generate(
                ORGANIZATION_ID, VPP_ID, BigDecimal.ZERO, BigDecimal.ZERO
        ));
        assertThrows(IllegalArgumentException.class, () -> service.generate(
                ORGANIZATION_ID, VPP_ID, BigDecimal.ONE, new BigDecimal("101")
        ));
    }

    @Test
    void freezesCoveredBaselineAndRejectsExpiredPreviewSource() {
        UUID previewId = UUID.randomUUID();
        Instant startAt = NOW.plusSeconds(900);
        var preview = preview(previewId);
        when(optimizationRepository.find(ORGANIZATION_ID, VPP_ID, previewId))
                .thenReturn(Optional.of(preview));
        when(optimizationRepository.isSourceSnapshotValid(ORGANIZATION_ID, previewId, NOW))
                .thenReturn(true);
        when(forecastRepository.latest(ORGANIZATION_ID, VPP_ID))
                .thenReturn(Optional.of(forecast(startAt)));

        var input = service.dispatchInput(
                ORGANIZATION_ID, VPP_ID, previewId, startAt, startAt.plusSeconds(1800)
        );

        assertEquals(2, input.baselinePoints().size());
        assertEquals(1, input.allocations().size());

        when(optimizationRepository.isSourceSnapshotValid(ORGANIZATION_ID, previewId, NOW))
                .thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.dispatchInput(
                ORGANIZATION_ID, VPP_ID, previewId, startAt, startAt.plusSeconds(1800)
        ));
    }

    private static FlexibilitySnapshot snapshot(
            Instant validUntil,
            List<FlexibilityCandidate> candidates
    ) {
        return new FlexibilitySnapshot(
                UUID.randomUUID(), ORGANIZATION_ID, VPP_ID, 3, Duration.ofHours(1),
                NOW.minusSeconds(30), validUntil, new BigDecimal("8"),
                new BigDecimal("6"), candidates
        );
    }

    private static FlexibilityCandidate candidate(
            String deviceId,
            String type,
            String power,
            String energy,
            String reason
    ) {
        return new FlexibilityCandidate(
                UUID.randomUUID(), UUID.fromString(deviceId), type,
                new BigDecimal(power), new BigDecimal(power), new BigDecimal(energy), reason
        );
    }

    private static OptimizationPreview preview(UUID previewId) {
        var allocated = new OptimizationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), "BATTERY",
                new BigDecimal("6"), new BigDecimal("3"), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("5"), true
        );
        var unused = new OptimizationCandidate(
                UUID.randomUUID(), UUID.randomUUID(), "EV_CHARGER",
                new BigDecimal("2"), BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ZERO, true
        );
        return new OptimizationPreview(
                previewId, ORGANIZATION_ID, VPP_ID, 2, UUID.randomUUID(), 3,
                new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("5"),
                new BigDecimal("5"), true, "V1", NOW, List.of(allocated, unused)
        );
    }

    private static Forecast forecast(Instant startAt) {
        return new Forecast(
                UUID.randomUUID(), ORGANIZATION_ID, VPP_ID, 4, ForecastHorizon.HOUR_1,
                "persistence-v1", "1.0", NOW, NOW.minusSeconds(3600), NOW,
                startAt, startAt.plusSeconds(3600), NOW.plusSeconds(3600),
                List.of(
                        new ForecastPoint(startAt, new BigDecimal("20"), BigDecimal.ZERO),
                        new ForecastPoint(startAt.plusSeconds(900), new BigDecimal("19"), BigDecimal.ZERO),
                        new ForecastPoint(startAt.plusSeconds(1800), new BigDecimal("18"), BigDecimal.ZERO)
                )
        );
    }
}
