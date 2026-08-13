package io.voltweave.intelligence.optimization.api.response;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.intelligence.optimization.application.model.AutomationPlan;

public record AutomationPlanResponse(
        Instant scheduledStartAt,
        long durationMinutes,
        UUID optimizationPreviewId,
        boolean feasible
) {
    public static AutomationPlanResponse from(AutomationPlan plan) {
        return new AutomationPlanResponse(
                plan.scheduledStartAt(), plan.duration().toMinutes(),
                plan.preview().id(), plan.preview().feasible()
        );
    }
}
