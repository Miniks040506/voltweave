package io.voltweave.dispatch.application.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record AutomationPlan(
        Instant scheduledStartAt,
        Duration duration,
        UUID optimizationPreviewId,
        boolean feasible
) {
}
