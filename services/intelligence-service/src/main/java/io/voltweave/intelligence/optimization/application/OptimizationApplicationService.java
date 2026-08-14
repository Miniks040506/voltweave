package io.voltweave.intelligence.optimization.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.intelligence.domain.WeightedAllocator;
import io.voltweave.intelligence.domain.WeightedAllocator.CandidateResource;
import io.voltweave.intelligence.domain.WeightedAllocator.Weights;
import io.voltweave.intelligence.flexibility.application.model.FlexibilityCandidate;
import io.voltweave.intelligence.flexibility.persistence.FlexibilityRepository;
import io.voltweave.intelligence.forecast.persistence.ForecastRepository;
import io.voltweave.intelligence.optimization.application.model.DispatchInput;
import io.voltweave.intelligence.optimization.application.model.OptimizationCandidate;
import io.voltweave.intelligence.optimization.application.model.OptimizationPreview;
import io.voltweave.intelligence.optimization.persistence.OptimizationRepository;

@Service
public class OptimizationApplicationService {
    private static final Weights WEIGHTS = Weights.V1;
    private static final String WEIGHT_VERSION = "V1";
    private static final BigDecimal ZERO_POWER = BigDecimal.ZERO.setScale(3);

    private final FlexibilityRepository flexibilityRepository;
    private final OptimizationRepository optimizationRepository;
    private final ForecastRepository forecastRepository;
    private final Clock clock;

    @Autowired
    public OptimizationApplicationService(
            FlexibilityRepository flexibilityRepository,
            OptimizationRepository optimizationRepository,
            ForecastRepository forecastRepository
    ) {
        this(flexibilityRepository, optimizationRepository, forecastRepository, Clock.systemUTC());
    }

    OptimizationApplicationService(
            FlexibilityRepository flexibilityRepository,
            OptimizationRepository optimizationRepository,
            ForecastRepository forecastRepository,
            Clock clock
    ) {
        this.flexibilityRepository = flexibilityRepository;
        this.optimizationRepository = optimizationRepository;
        this.forecastRepository = forecastRepository;
        this.clock = clock;
    }

    @Transactional
    public OptimizationPreview generate(
            UUID organizationId,
            UUID vppId,
            BigDecimal targetPowerKw,
            BigDecimal reserveMarginPercent
    ) {
        return generate(organizationId, vppId, targetPowerKw, reserveMarginPercent, Set.of());
    }

    @Transactional
    public OptimizationPreview generate(
            UUID organizationId,
            UUID vppId,
            BigDecimal targetPowerKw,
            BigDecimal reserveMarginPercent,
            Set<UUID> excludedDeviceIds
    ) {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        Objects.requireNonNull(excludedDeviceIds, "excludedDeviceIds is required");
        requirePositive(targetPowerKw, "targetPowerKw");
        requirePercentage(reserveMarginPercent, "reserveMarginPercent");

        Instant now = clock.instant();
        var snapshot = flexibilityRepository.latest(organizationId, vppId)
                .orElseThrow(() -> new IllegalStateException(
                        "A flexibility snapshot is required before optimization"
                ));
        if (!snapshot.validUntil().isAfter(now)) {
            throw new IllegalStateException("The latest flexibility snapshot has expired");
        }

        BigDecimal maxEnergy = snapshot.candidates().stream()
                .filter(candidate -> eligible(candidate))
                .map(FlexibilityCandidate::availableEnergyKwh)
                .max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        var resources = snapshot.candidates().stream()
                .map(candidate -> resource(
                        candidate, maxEnergy, !excludedDeviceIds.contains(candidate.deviceId())
                )).toList();
        var plan = WeightedAllocator.allocate(
                resources, targetPowerKw.doubleValue(), reserveMarginPercent.doubleValue(), WEIGHTS
        );
        var allocations = new HashMap<String, WeightedAllocator.Allocation>();
        plan.allocations().forEach(value -> allocations.put(value.deviceId(), value));

        List<OptimizationCandidate> candidates = snapshot.candidates().stream()
                .map(candidate -> optimized(
                        candidate, maxEnergy, allocations,
                        !excludedDeviceIds.contains(candidate.deviceId())
                ))
                .sorted(Comparator.comparing(OptimizationCandidate::allocatedPowerKw).reversed()
                        .thenComparing(Comparator.comparing(
                                OptimizationCandidate::score
                        ).reversed())
                        .thenComparing(OptimizationCandidate::deviceId))
                .toList();
        var preview = new OptimizationPreview(
                UUID.randomUUID(), organizationId, vppId,
                optimizationRepository.nextVersion(organizationId, vppId, now),
                snapshot.id(), snapshot.version(), power(targetPowerKw),
                power(reserveMarginPercent), power(plan.requiredPowerKw()),
                power(plan.plannedPowerKw()), plan.feasible(), WEIGHT_VERSION, now, candidates
        );
        optimizationRepository.insert(preview, WEIGHTS);
        return preview;
    }

    @Transactional(readOnly = true)
    public Optional<OptimizationPreview> latest(UUID organizationId, UUID vppId) {
        return optimizationRepository.latest(organizationId, vppId);
    }

    @Transactional(readOnly = true)
    public DispatchInput dispatchInput(
            UUID organizationId,
            UUID vppId,
            UUID previewId,
            Instant startAt,
            Instant endAt
    ) {
        Instant now = clock.instant();
        if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("Dispatch interval is invalid");
        }
        var preview = optimizationRepository.find(organizationId, vppId, previewId)
                .orElseThrow(() -> new IllegalStateException("Optimization preview is unavailable"));
        if (!optimizationRepository.isSourceSnapshotValid(organizationId, previewId, now)) {
            throw new IllegalStateException("Optimization preview source snapshot has expired");
        }
        var forecast = forecastRepository.latest(organizationId, vppId)
                .filter(value -> value.validUntil().isAfter(now))
                .orElseThrow(() -> new IllegalStateException("A valid forecast baseline is required"));
        var baselinePoints = forecast.points().stream()
                .filter(point -> !point.forecastAt().isBefore(startAt)
                        && point.forecastAt().isBefore(endAt))
                .map(point -> new DispatchInput.BaselinePoint(
                        point.forecastAt(), point.baselineGridImportKw()
                )).toList();
        long expectedPoints = java.time.Duration.between(startAt, endAt).toMinutes() / 15;
        if (baselinePoints.size() != expectedPoints) {
            throw new IllegalStateException("Forecast does not cover the dispatch interval");
        }
        return new DispatchInput(
                preview.id(), preview.version(), organizationId, vppId,
                preview.targetPowerKw(), preview.requiredPowerKw(), preview.plannedPowerKw(),
                preview.feasible(), forecast.id(), forecast.version(), forecast.modelName(),
                forecast.modelVersion(), forecast.validUntil(),
                preview.candidates().stream().filter(candidate ->
                        candidate.allocatedPowerKw().signum() > 0).toList(), baselinePoints
        );
    }

    private static CandidateResource resource(
            FlexibilityCandidate candidate,
            BigDecimal maxEnergy,
            boolean included
    ) {
        boolean eligible = included && eligible(candidate);
        return new CandidateResource(
                candidate.deviceId().toString(), candidate.upwardFlexibilityKw().doubleValue(),
                eligible ? 1.0 : 0.0, availableSoc(candidate, maxEnergy),
                eligible ? responseSpeed(candidate.deviceType()) : 0.0,
                eligible ? degradationCost(candidate.deviceType()) : 0.0,
                eligible ? 1.0 : 0.0, eligible
        );
    }

    private static OptimizationCandidate optimized(
            FlexibilityCandidate candidate,
            BigDecimal maxEnergy,
            Map<String, WeightedAllocator.Allocation> allocations,
            boolean included
    ) {
        var resource = resource(candidate, maxEnergy, included);
        var allocation = allocations.get(resource.deviceId());
        return new OptimizationCandidate(
                candidate.siteId(), candidate.deviceId(), candidate.deviceType(),
                candidate.upwardFlexibilityKw(), candidate.availableEnergyKwh(),
                factor(resource.reliability()), factor(resource.availableSoc()),
                factor(resource.responseSpeed()), factor(resource.lowDegradationCost()),
                factor(resource.customerPreference()), factor(WEIGHTS.score(resource)),
                allocation == null ? ZERO_POWER : power(allocation.powerKw()),
                resource.eligible()
        );
    }

    private static boolean eligible(FlexibilityCandidate candidate) {
        return candidate.upwardFlexibilityKw().signum() > 0;
    }

    private static double availableSoc(FlexibilityCandidate candidate, BigDecimal maxEnergy) {
        if (!eligible(candidate) || maxEnergy.signum() == 0) {
            return 0.0;
        }
        return candidate.availableEnergyKwh().divide(maxEnergy, 8, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE).doubleValue();
    }

    private static double responseSpeed(String deviceType) {
        return "BATTERY".equals(deviceType) ? 1.0 : 0.8;
    }

    private static double degradationCost(String deviceType) {
        return "EV_CHARGER".equals(deviceType) ? 1.0 : 0.8;
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePercentage(BigDecimal value, String name) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException(name + " must be between 0 and 100");
        }
    }

    private static BigDecimal power(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal power(BigDecimal value) {
        return value.setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal factor(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
