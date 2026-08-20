package io.voltweave.dispatch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.application.model.DispatchPerformance;
import io.voltweave.dispatch.persistence.PerformanceRepository;
import io.voltweave.dispatch.persistence.PerformanceRepository.PerformancePoint;

@Service
public class PerformanceApplicationService {
    private static final BigDecimal HOUR_SECONDS = BigDecimal.valueOf(3_600);

    private final PerformanceRepository repository;

    public PerformanceApplicationService(PerformanceRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            UUID eventId,
            UUID organizationId,
            TelemetryNormalizedPayloadV1 telemetry,
            Instant recordedAt
    ) {
        for (var allocation : repository.findActiveAllocations(
                organizationId, telemetry.siteId(), telemetry.deviceId(), telemetry.observedAt()
        )) {
            if (!allocation.deviceType().equals(telemetry.deviceType())) {
                throw new IllegalArgumentException("Telemetry device type does not match allocation");
            }
            var previous = repository.lastPoint(allocation.dispatchId(), telemetry.deviceId());
            if (previous != null && !telemetry.observedAt().isAfter(previous.observedAt())) {
                continue;
            }

            BigDecimal delivered = deliveredPower(
                    allocation.deviceType(), allocation.sourceAvailablePowerKw(),
                    telemetry.activePowerKw(), telemetry.online()
            );
            BigDecimal error = allocation.requestedPowerKw().subtract(delivered)
                    .setScale(3, RoundingMode.HALF_UP);
            BigDecimal errorPercent = error.multiply(BigDecimal.valueOf(100))
                    .divide(allocation.requestedPowerKw(), 3, RoundingMode.HALF_UP);
            BigDecimal energy = cumulativeEnergy(previous, telemetry.observedAt(), delivered);
            repository.insert(new PerformancePoint(
                    organizationId, allocation.dispatchId(), telemetry.siteId(),
                    telemetry.deviceId(), eventId, telemetry.observedAt(),
                    allocation.targetPowerKw(), allocation.requestedPowerKw(),
                    telemetry.activePowerKw(), delivered, error, errorPercent,
                    energy, telemetry.online(), recordedAt
            ));
        }
    }

    @Transactional(readOnly = true)
    public Optional<DispatchPerformance> find(UUID organizationId, UUID dispatchId) {
        return find(
                organizationId,
                dispatchId,
                repository.originalRequestedPower(organizationId, dispatchId)
        );
    }

    private Optional<DispatchPerformance> find(
            UUID organizationId,
            UUID dispatchId,
            BigDecimal frozenRequestedPowerKw
    ) {
        var points = repository.findPoints(organizationId, dispatchId);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        var latest = points.stream().collect(java.util.stream.Collectors.toMap(
                DispatchPerformance.Point::deviceId,
                point -> point,
                (first, second) -> second
        )).values();
        BigDecimal requested = sum(latest.stream()
                .map(DispatchPerformance.Point::requestedPowerKw).toList())
                .min(frozenRequestedPowerKw);
        BigDecimal delivered = sum(latest.stream()
                .map(DispatchPerformance.Point::deliveredPowerKw).toList());
        BigDecimal energy = sum(latest.stream()
                .map(DispatchPerformance.Point::cumulativeDeliveredEnergyKwh).toList());
        BigDecimal error = requested.subtract(delivered).setScale(3, RoundingMode.HALF_UP);
        BigDecimal achievement = delivered.multiply(BigDecimal.valueOf(100))
                .divide(requested, 3, RoundingMode.HALF_UP);
        return Optional.of(new DispatchPerformance(
                dispatchId, requested, delivered, error, achievement, energy, points
        ));
    }

    public DispatchPerformance get(Dispatch dispatch) {
        BigDecimal requested = sum(dispatch.allocations().stream()
                .map(Dispatch.Allocation::allocatedPowerKw).toList());
        return find(dispatch.organizationId(), dispatch.id(), requested).orElseGet(() -> {
            return new DispatchPerformance(
                    dispatch.id(), requested, BigDecimal.ZERO.setScale(3), requested,
                    BigDecimal.ZERO.setScale(3), BigDecimal.ZERO.setScale(6), List.of()
            );
        });
    }

    private static BigDecimal deliveredPower(
            String deviceType,
            BigDecimal sourceAvailablePowerKw,
            BigDecimal actualPowerKw,
            boolean online
    ) {
        if (!online) {
            return BigDecimal.ZERO.setScale(3);
        }
        BigDecimal delivered = switch (deviceType) {
            case "BATTERY" -> actualPowerKw.negate();
            case "EV_CHARGER" -> sourceAvailablePowerKw.subtract(actualPowerKw);
            default -> throw new IllegalArgumentException(
                    "Unsupported dispatch device type: " + deviceType
            );
        };
        return delivered.max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal cumulativeEnergy(
            PerformanceRepository.LastPoint previous,
            Instant observedAt,
            BigDecimal deliveredPowerKw
    ) {
        if (previous == null) {
            return BigDecimal.ZERO.setScale(6);
        }
        BigDecimal averagePower = previous.deliveredPowerKw().add(deliveredPowerKw)
                .divide(BigDecimal.valueOf(2), 6, RoundingMode.HALF_UP);
        BigDecimal intervalEnergy = averagePower
                .multiply(BigDecimal.valueOf(Duration.between(
                        previous.observedAt(), observedAt
                ).toMillis()))
                .divide(BigDecimal.valueOf(1_000).multiply(HOUR_SECONDS), 6,
                        RoundingMode.HALF_UP);
        return previous.cumulativeDeliveredEnergyKwh().add(intervalEnergy)
                .setScale(6, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(java.util.List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
