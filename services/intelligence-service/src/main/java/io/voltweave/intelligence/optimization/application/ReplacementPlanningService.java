package io.voltweave.intelligence.optimization.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import io.voltweave.intelligence.flexibility.application.FlexibilitySnapshotApplicationService;
import io.voltweave.intelligence.optimization.application.model.OptimizationPreview;

@Service
public class ReplacementPlanningService {
    private final FlexibilitySnapshotApplicationService flexibilityService;
    private final OptimizationApplicationService optimizationService;

    public ReplacementPlanningService(
            FlexibilitySnapshotApplicationService flexibilityService,
            OptimizationApplicationService optimizationService
    ) {
        this.flexibilityService = flexibilityService;
        this.optimizationService = optimizationService;
    }

    public OptimizationPreview plan(
            UUID organizationId,
            UUID vppId,
            BigDecimal missingPowerKw,
            BigDecimal reserveMarginPercent,
            Duration remainingDuration,
            Set<UUID> excludedDeviceIds
    ) {
        flexibilityService.generate(organizationId, vppId, remainingDuration);
        return optimizationService.generate(
                organizationId, vppId, missingPowerKw,
                reserveMarginPercent, excludedDeviceIds
        );
    }
}

