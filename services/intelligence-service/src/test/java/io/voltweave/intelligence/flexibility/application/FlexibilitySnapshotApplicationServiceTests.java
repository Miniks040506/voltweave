package io.voltweave.intelligence.flexibility.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import org.junit.jupiter.api.Test;

import io.voltweave.intelligence.access.PortfolioFlexibilityClient;
import io.voltweave.intelligence.access.PortfolioFlexibilityClient.PortfolioFlexibilityResource;
import io.voltweave.intelligence.flexibility.application.model.DeviceTelemetry;
import io.voltweave.intelligence.flexibility.persistence.FlexibilityRepository;

import static org.mockito.Mockito.mock;

class FlexibilitySnapshotApplicationServiceTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000020"
    );
    private static final UUID VPP_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000020"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000020"
    );
    private static final UUID BATTERY_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000001"
    );
    private static final UUID EV_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000002"
    );
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    private final PortfolioFlexibilityClient portfolioClient = mock(
            PortfolioFlexibilityClient.class
    );
    private final FlexibilityRepository repository = mock(FlexibilityRepository.class);
    private final FlexibilitySnapshotApplicationService service =
            new FlexibilitySnapshotApplicationService(
                    portfolioClient, repository, Duration.ofMinutes(5), Duration.ofSeconds(30),
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void capsAllCandidatesByTheCurrentSiteImport() {
        when(portfolioClient.resourcesForVpp(VPP_ID)).thenReturn(List.of(battery(), ev()));
        when(repository.latestTelemetry(BATTERY_ID)).thenReturn(Optional.of(
                telemetry(BATTERY_ID, "BATTERY", "0", "80")
        ));
        when(repository.latestTelemetry(EV_ID)).thenReturn(Optional.of(
                telemetry(EV_ID, "EV_CHARGER", "4", "80")
        ));
        when(repository.latestSiteTelemetry(ORGANIZATION_ID, SITE_ID, "SMART_METER"))
                .thenReturn(Optional.of(
                telemetry(UUID.randomUUID(), "SMART_METER", "6", null)
        ));
        when(repository.nextVersion(ORGANIZATION_ID, VPP_ID, NOW)).thenReturn(7L);

        var snapshot = service.generate(ORGANIZATION_ID, VPP_ID, Duration.ofHours(1));

        assertEquals(7L, snapshot.version());
        assertEquals(0, snapshot.upwardFlexibilityKw().compareTo(new BigDecimal("6.000")));
        assertEquals(0, snapshot.availableEnergyKwh().compareTo(new BigDecimal("6.000")));
        assertEquals(0, snapshot.candidates().get(0).upwardFlexibilityKw()
                .compareTo(new BigDecimal("5.000")));
        assertEquals(0, snapshot.candidates().get(1).upwardFlexibilityKw()
                .compareTo(new BigDecimal("1.000")));
        assertEquals(0, snapshot.candidates().get(1).sourcePowerKw()
                .compareTo(new BigDecimal("4.000")));
        assertEquals("SITE_IMPORT_LIMIT", snapshot.candidates().get(1).limitingReason());
        verify(repository).insert(snapshot);
    }

    @Test
    void failsSafeWhenTheSiteMeterIsNotUsable() {
        when(portfolioClient.resourcesForVpp(VPP_ID)).thenReturn(List.of(battery()));
        when(repository.latestTelemetry(BATTERY_ID)).thenReturn(Optional.of(
                telemetry(BATTERY_ID, "BATTERY", "0", "80")
        ));
        when(repository.latestSiteTelemetry(ORGANIZATION_ID, SITE_ID, "SMART_METER"))
                .thenReturn(Optional.empty());
        when(repository.nextVersion(ORGANIZATION_ID, VPP_ID, NOW)).thenReturn(1L);

        var snapshot = service.generate(ORGANIZATION_ID, VPP_ID, Duration.ofHours(1));

        assertEquals(0, snapshot.upwardFlexibilityKw().compareTo(new BigDecimal("0.000")));
        assertEquals("SITE_METER_UNAVAILABLE", snapshot.candidates().getFirst()
                .limitingReason());
    }

    @Test
    void rejectsTelemetryFromAnotherTenant() {
        when(portfolioClient.resourcesForVpp(VPP_ID)).thenReturn(List.of(battery()));
        var wrongTenant = telemetry(BATTERY_ID, "BATTERY", "0", "80");
        when(repository.latestTelemetry(BATTERY_ID)).thenReturn(Optional.of(
                new DeviceTelemetry(
                        UUID.randomUUID(), wrongTenant.deviceId(), wrongTenant.siteId(),
                        wrongTenant.deviceType(), wrongTenant.observedAt(),
                        wrongTenant.receivedAt(), wrongTenant.activePowerKw(),
                        wrongTenant.socPercent(), wrongTenant.online(), wrongTenant.quality()
                )
        ));
        when(repository.latestSiteTelemetry(ORGANIZATION_ID, SITE_ID, "SMART_METER"))
                .thenReturn(Optional.of(
                        telemetry(UUID.randomUUID(), "SMART_METER", "6", null)
                ));
        when(repository.nextVersion(ORGANIZATION_ID, VPP_ID, NOW)).thenReturn(1L);

        var snapshot = service.generate(ORGANIZATION_ID, VPP_ID, Duration.ofHours(1));

        assertEquals("TELEMETRY_MISMATCH", snapshot.candidates().getFirst().limitingReason());
        assertEquals(0, snapshot.upwardFlexibilityKw().compareTo(new BigDecimal("0.000")));
    }

    private static PortfolioFlexibilityResource battery() {
        return new PortfolioFlexibilityResource(
                ORGANIZATION_ID, SITE_ID, BATTERY_ID, "BATTERY", "PROVISIONED", true,
                new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("5"), 20,
                BigDecimal.ONE, null, null, null, null, null
        );
    }

    private static PortfolioFlexibilityResource ev() {
        return new PortfolioFlexibilityResource(
                ORGANIZATION_ID, SITE_ID, EV_ID, "EV_CHARGER", "PROVISIONED", true,
                new BigDecimal("7"), null, null, null, null,
                new BigDecimal("7"), new BigDecimal("60"), 80, BigDecimal.ONE,
                NOW.plus(Duration.ofHours(4))
        );
    }

    private static DeviceTelemetry telemetry(
            UUID deviceId,
            String type,
            String power,
            String soc
    ) {
        return new DeviceTelemetry(
                ORGANIZATION_ID, deviceId, SITE_ID, type, NOW, NOW, new BigDecimal(power),
                soc == null ? null : new BigDecimal(soc), true, "VALID"
        );
    }
}
