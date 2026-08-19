package io.voltweave.intelligence.flexibility.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.intelligence.access.PortfolioFlexibilityClient;
import io.voltweave.intelligence.access.PortfolioFlexibilityClient.PortfolioFlexibilityResource;
import io.voltweave.intelligence.domain.BatteryFlexibility;
import io.voltweave.intelligence.domain.EvChargingRequirement;
import io.voltweave.intelligence.domain.EvFlexibility;
import io.voltweave.intelligence.flexibility.application.model.DeviceTelemetry;
import io.voltweave.intelligence.flexibility.application.model.FlexibilityCandidate;
import io.voltweave.intelligence.flexibility.application.model.FlexibilitySnapshot;
import io.voltweave.intelligence.flexibility.persistence.FlexibilityRepository;

@Service
public class FlexibilitySnapshotApplicationService {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(3);

    private final PortfolioFlexibilityClient portfolioClient;
    private final FlexibilityRepository repository;
    private final Duration telemetryFreshness;
    private final Duration futureSkew;
    private final Clock clock;

    @Autowired
    public FlexibilitySnapshotApplicationService(
            PortfolioFlexibilityClient portfolioClient,
            FlexibilityRepository repository,
            @Value("${voltweave.flexibility.freshness:5m}") Duration telemetryFreshness,
            @Value("${voltweave.flexibility.future-skew:30s}") Duration futureSkew
    ) {
        this(portfolioClient, repository, telemetryFreshness, futureSkew, Clock.systemUTC());
    }

    FlexibilitySnapshotApplicationService(
            PortfolioFlexibilityClient portfolioClient,
            FlexibilityRepository repository,
            Duration telemetryFreshness,
            Duration futureSkew,
            Clock clock
    ) {
        if (telemetryFreshness.isZero() || telemetryFreshness.isNegative()) {
            throw new IllegalArgumentException("telemetry freshness must be positive");
        }
        if (futureSkew.isNegative()) {
            throw new IllegalArgumentException("future skew must not be negative");
        }
        this.portfolioClient = portfolioClient;
        this.repository = repository;
        this.telemetryFreshness = telemetryFreshness;
        this.futureSkew = futureSkew;
        this.clock = clock;
    }

    @Transactional
    public FlexibilitySnapshot generate(
            UUID organizationId,
            UUID vppId,
            Duration dispatchDuration
    ) {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        validateDuration(dispatchDuration);
        Instant now = clock.instant();
        List<PortfolioFlexibilityResource> resources = portfolioClient.resourcesForVpp(vppId)
                .stream().sorted(Comparator.comparing(PortfolioFlexibilityResource::siteId)
                        .thenComparing(PortfolioFlexibilityResource::deviceId)).toList();
        var candidates = new ArrayList<FlexibilityCandidate>();
        for (var siteResources : resources.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        PortfolioFlexibilityResource::siteId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                )).values()) {
            candidates.addAll(candidatesForSite(siteResources, now, dispatchDuration));
        }
        BigDecimal upwardPower = candidates.stream().map(FlexibilityCandidate::upwardFlexibilityKw)
                .reduce(ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
        BigDecimal availableEnergy = candidates.stream().map(FlexibilityCandidate::availableEnergyKwh)
                .reduce(ZERO, BigDecimal::add).setScale(3, RoundingMode.HALF_UP);
        var snapshot = new FlexibilitySnapshot(
                UUID.randomUUID(), organizationId, vppId,
                repository.nextVersion(organizationId, vppId, now), dispatchDuration,
                now, now.plus(telemetryFreshness), upwardPower, availableEnergy, candidates
        );
        repository.insert(snapshot);
        return snapshot;
    }

    @Transactional(readOnly = true)
    public java.util.Optional<FlexibilitySnapshot> latest(UUID organizationId, UUID vppId) {
        return repository.latest(organizationId, vppId);
    }

    private List<FlexibilityCandidate> candidatesForSite(
            List<PortfolioFlexibilityResource> resources,
            Instant now,
            Duration dispatchDuration
    ) {
        var drafts = resources.stream().map(resource -> draft(resource, now, dispatchDuration))
                .toList();
        var firstResource = resources.getFirst();
        var meter = repository.latestSiteTelemetry(
                firstResource.organizationId(), firstResource.siteId(), "SMART_METER"
        )
                .filter(value -> usable(value, now));
        if (meter.isEmpty() || meter.orElseThrow().activePowerKw().signum() <= 0) {
            String reason = meter.isEmpty()
                    ? "SITE_METER_UNAVAILABLE" : "NO_SITE_IMPORT_HEADROOM";
            return drafts.stream().map(draft -> draft.finish(ZERO,
                    draft.reason() == null ? reason : draft.reason(), dispatchDuration)).toList();
        }
        BigDecimal remaining = meter.orElseThrow().activePowerKw();
        var candidates = new ArrayList<FlexibilityCandidate>();
        for (var draft : drafts) {
            BigDecimal available = draft.rawPowerKw().min(remaining);
            remaining = remaining.subtract(available);
            String reason = draft.reason();
            if (reason == null && available.compareTo(draft.rawPowerKw()) < 0) {
                reason = "SITE_IMPORT_LIMIT";
            }
            candidates.add(draft.finish(available, reason, dispatchDuration));
        }
        return candidates;
    }

    private CandidateDraft draft(
            PortfolioFlexibilityResource resource,
            Instant now,
            Duration dispatchDuration
    ) {
        if (!resource.vppOptIn()) {
            return unavailable(resource, "SITE_OPTED_OUT");
        }
        if (!"PROVISIONED".equals(resource.status())) {
            return unavailable(resource, "DEVICE_NOT_PROVISIONED");
        }
        var telemetry = repository.latestTelemetry(resource.deviceId());
        if (telemetry.isEmpty() || !usable(telemetry.orElseThrow(), now)) {
            return unavailable(resource, "TELEMETRY_UNAVAILABLE");
        }
        DeviceTelemetry value = telemetry.orElseThrow();
        if (!resource.organizationId().equals(value.organizationId())
                || !resource.siteId().equals(value.siteId())
                || !resource.deviceType().equals(value.deviceType())) {
            return unavailable(resource, "TELEMETRY_MISMATCH");
        }
        if (value.socPercent() == null) {
            return unavailable(resource, "SOC_UNAVAILABLE");
        }
        try {
            double rawPower = switch (resource.deviceType()) {
                case "BATTERY" -> new BatteryFlexibility(
                        resource.capacityKwh().doubleValue(), value.socPercent().doubleValue(),
                        resource.minimumSocPercent(), resource.maxDischargeKw().doubleValue(),
                        resource.dischargeEfficiency().doubleValue(),
                        resource.maxDischargeKw().doubleValue(), true, true, true, true, false
                ).availablePowerKw(dispatchDuration);
                case "EV_CHARGER" -> evPower(resource, value, now, dispatchDuration);
                default -> 0.0;
            };
            if (rawPower <= 0.0) {
                return unavailable(resource, "EV_CHARGER".equals(resource.deviceType())
                        ? "DEPARTURE_CONSTRAINT" : "NO_AVAILABLE_ENERGY");
            }
            BigDecimal sourcePower = "EV_CHARGER".equals(resource.deviceType())
                    ? value.activePowerKw().setScale(3, RoundingMode.HALF_UP)
                    : rounded(rawPower);
            return new CandidateDraft(resource, sourcePower, rounded(rawPower), null);
        } catch (NullPointerException | IllegalArgumentException exception) {
            return unavailable(resource, "CONFIGURATION_INVALID");
        }
    }

    private static double evPower(
            PortfolioFlexibilityResource resource,
            DeviceTelemetry telemetry,
            Instant now,
            Duration dispatchDuration
    ) {
        if (telemetry.activePowerKw().signum() <= 0) {
            return 0.0;
        }
        var requirement = new EvChargingRequirement(
                resource.vehicleBatteryCapacityKwh().doubleValue(),
                telemetry.socPercent().doubleValue(), resource.targetSocPercent(),
                resource.maxChargingKw().doubleValue(), resource.chargingEfficiency().doubleValue()
        );
        return new EvFlexibility(requirement, telemetry.activePowerKw().doubleValue(),
                true, true, true, false)
                .curtailablePowerKw(now, resource.departureAt(), dispatchDuration);
    }

    private boolean usable(DeviceTelemetry value, Instant now) {
        return value.online() && "VALID".equals(value.quality())
                && !value.observedAt().isBefore(now.minus(telemetryFreshness))
                && !value.observedAt().isAfter(now.plus(futureSkew));
    }

    private static CandidateDraft unavailable(
            PortfolioFlexibilityResource resource,
            String reason
    ) {
        return new CandidateDraft(resource, ZERO, ZERO, reason);
    }

    private static void validateDuration(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("dispatch duration must be positive");
        }
    }

    private static BigDecimal rounded(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private record CandidateDraft(
            PortfolioFlexibilityResource resource,
            BigDecimal sourcePowerKw,
            BigDecimal rawPowerKw,
            String reason
    ) {
        FlexibilityCandidate finish(
                BigDecimal powerKw,
                String unavailableReason,
                Duration duration
        ) {
            BigDecimal energy = powerKw.multiply(BigDecimal.valueOf(duration.toSeconds()))
                    .divide(BigDecimal.valueOf(3_600), 3, RoundingMode.HALF_UP);
            return new FlexibilityCandidate(
                    resource.siteId(), resource.deviceId(), resource.deviceType(), sourcePowerKw,
                    rawPowerKw, powerKw, energy, unavailableReason
            );
        }
    }
}
