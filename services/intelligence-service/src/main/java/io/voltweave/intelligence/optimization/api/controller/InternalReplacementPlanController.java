package io.voltweave.intelligence.optimization.api.controller;

import java.time.Duration;
import java.util.UUID;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.intelligence.optimization.api.request.PlanReplacementRequest;
import io.voltweave.intelligence.optimization.api.response.OptimizationPreviewResponse;
import io.voltweave.intelligence.optimization.application.ReplacementPlanningService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/v1/vpps/{vppId}/replacement-plans")
public class InternalReplacementPlanController {
    private final ReplacementPlanningService planningService;

    public InternalReplacementPlanController(ReplacementPlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping
    public OptimizationPreviewResponse plan(
            @PathVariable UUID vppId,
            @RequestParam UUID organizationId,
            @Valid @RequestBody PlanReplacementRequest request
    ) {
        return OptimizationPreviewResponse.from(planningService.plan(
                organizationId, vppId, request.missingPowerKw(),
                request.reserveMarginPercent(), Duration.ofSeconds(request.remainingSeconds()),
                request.excludedDeviceIds()
        ));
    }
}
