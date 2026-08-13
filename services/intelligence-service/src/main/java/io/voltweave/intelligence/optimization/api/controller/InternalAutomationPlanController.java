package io.voltweave.intelligence.optimization.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.intelligence.optimization.api.request.PlanAutomationRequest;
import io.voltweave.intelligence.optimization.api.response.AutomationPlanResponse;
import io.voltweave.intelligence.optimization.application.AutomationPlanningService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/internal/v1/vpps/{vppId}/automation-plans")
public class InternalAutomationPlanController {
    private final AutomationPlanningService planningService;

    public InternalAutomationPlanController(AutomationPlanningService planningService) {
        this.planningService = planningService;
    }

    @PostMapping
    public ResponseEntity<AutomationPlanResponse> plan(
            @PathVariable UUID vppId,
            @Valid @RequestBody PlanAutomationRequest request
    ) {
        var plan = switch (request.triggerType()) {
            case "PEAK_LIMIT" -> planningService.peakLimitPlan(
                    request.organizationId(), vppId, request.peakImportLimitKw(),
                    request.maxDispatchPowerKw(), request.maxDispatchDurationMinutes(),
                    request.reserveMarginPercent()
            );
            case "PRICE_THRESHOLD" -> planningService.priceThresholdPlan(
                    request.organizationId(), vppId, request.priceThreshold(),
                    request.maxDispatchPowerKw(), request.maxDispatchDurationMinutes(),
                    request.reserveMarginPercent()
            );
            default -> throw new IllegalArgumentException("Unsupported automation trigger");
        };
        return ResponseEntity.of(plan.map(AutomationPlanResponse::from));
    }
}
