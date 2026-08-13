package io.voltweave.telemetry.command.application.model;

import java.time.Instant;
import java.util.UUID;

public record CommandDelivery(
        UUID commandId,
        UUID organizationId,
        UUID dispatchId,
        UUID siteId,
        UUID deviceId,
        String mqttTopic,
        String mqttPayload,
        Instant validFrom,
        Instant acknowledgementDeadlineAt,
        Instant expiresAt,
        int attempts
) {
}
