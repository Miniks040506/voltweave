package io.voltweave.dispatch.application.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record CreateDispatchCommand(
        UUID organizationId,
        UUID vppId,
        UUID optimizationPreviewId,
        String type,
        Instant scheduledStartAt,
        Duration duration,
        String createdBy,
        String idempotencyKey
) {
}
