package io.voltweave.dispatch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.PortfolioAccessClient;
import io.voltweave.dispatch.application.model.ReplacementAllocation;
import io.voltweave.dispatch.persistence.RebalanceRepository;

@Service
public class RebalanceApplicationService {
    private final RebalanceRepository repository;
    private final PortfolioAccessClient portfolioClient;
    private final IntelligenceDispatchClient intelligenceClient;
    private final CommandApplicationService commandService;
    private final Duration telemetryFreshness;

    public RebalanceApplicationService(
            RebalanceRepository repository,
            PortfolioAccessClient portfolioClient,
            IntelligenceDispatchClient intelligenceClient,
            CommandApplicationService commandService,
            @Value("${voltweave.performance.stale-after:15s}") Duration telemetryFreshness
    ) {
        this.repository = repository;
        this.portfolioClient = portfolioClient;
        this.intelligenceClient = intelligenceClient;
        this.commandService = commandService;
        if (telemetryFreshness.isZero() || telemetryFreshness.isNegative()) {
            throw new IllegalArgumentException("performance stale-after must be positive");
        }
        this.telemetryFreshness = telemetryFreshness;
    }

    @Transactional
    public void evaluate(UUID dispatchId, Instant now) {
        var dispatch = repository.lock(dispatchId).orElse(null);
        if (dispatch == null || !dispatch.scheduledEndAt().isAfter(now)) {
            return;
        }
        BigDecimal delivered = repository.currentDeliveredPower(
                dispatchId, now.minus(telemetryFreshness)
        );
        BigDecimal missing = dispatch.targetPowerKw().subtract(delivered)
                .max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP);
        var policy = portfolioClient.recoveryPolicy(
                dispatch.organizationId(), dispatch.vppId()
        );
        BigDecimal tolerated = dispatch.targetPowerKw()
                .multiply(BigDecimal.valueOf(policy.underDeliveryTolerancePercent()))
                .divide(BigDecimal.valueOf(100), 3, RoundingMode.HALF_UP);
        if (missing.compareTo(tolerated) <= 0) {
            repository.clearUnderDelivery(dispatchId);
            return;
        }
        if (dispatch.underDeliverySince() == null) {
            repository.markUnderDelivery(dispatchId, now);
            return;
        }
        if (now.isBefore(dispatch.underDeliverySince().plusSeconds(
                policy.underDeliveryGraceSeconds()
        )) || dispatch.lastRebalanceAt() != null && now.isBefore(
                dispatch.lastRebalanceAt().plusSeconds(policy.rebalanceCooldownSeconds())
        )) {
            return;
        }

        Duration remaining = Duration.between(now, dispatch.scheduledEndAt());
        if (remaining.toSeconds() < 1) {
            return;
        }
        var excluded = repository.unavailableDeviceIds(
                dispatchId, now, dispatch.scheduledEndAt()
        );
        var plan = intelligenceClient.replacementPlan(
                dispatch.organizationId(), dispatch.vppId(), missing,
                policy.reserveMarginPercent(), remaining, excluded
        );
        var allocations = plan.candidates().stream()
                .filter(candidate -> candidate.allocatedPowerKw().signum() > 0)
                .map(candidate -> new ReplacementAllocation(
                        candidate.siteId(), candidate.deviceId(), candidate.deviceType(),
                        candidate.sourcePowerKw(), candidate.allocatedPowerKw(),
                        expectedEnergy(candidate.allocatedPowerKw(), remaining), candidate.score()
                )).toList();
        BigDecimal replacementPower = allocations.stream()
                .map(ReplacementAllocation::allocatedPowerKw)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!plan.feasible() || replacementPower.compareTo(missing) < 0) {
            repository.insertRebalance(
                    dispatch, plan.id(), missing, plan.plannedPowerKw(), "FAILED", now
            );
            repository.transition(dispatchId, "ACTIVE", "FAILED", now);
            return;
        }

        UUID rebalanceId = repository.insertRebalance(
                dispatch, plan.id(), missing, plan.plannedPowerKw(), "COMMANDING", now
        );
        repository.insertAllocations(dispatch, rebalanceId, allocations, now);
        commandService.requestReplacements(
                dispatch.organizationId(), dispatchId, dispatch.scheduledEndAt(),
                allocations, UUID.randomUUID()
        );
        repository.transition(dispatchId, "ACTIVE", "REBALANCING", now);
    }

    private static BigDecimal expectedEnergy(BigDecimal powerKw, Duration duration) {
        return powerKw.multiply(BigDecimal.valueOf(duration.toMillis()))
                .divide(BigDecimal.valueOf(3_600_000), 3, RoundingMode.HALF_UP);
    }
}
