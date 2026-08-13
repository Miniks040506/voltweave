package io.voltweave.intelligence.optimization.application.model;

import java.time.Duration;
import java.time.Instant;

public record AutomationPlan(
        Instant scheduledStartAt,
        Duration duration,
        OptimizationPreview preview
) {
}
