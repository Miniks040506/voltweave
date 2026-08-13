package io.voltweave.dispatch.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.dispatch.domain.enums.CommandStatus;

public record DeviceCommand(
        UUID id,
        UUID organizationId,
        UUID dispatchId,
        UUID siteId,
        UUID deviceId,
        String commandType,
        BigDecimal targetPowerKw,
        Instant validFrom,
        Instant acknowledgementDeadlineAt,
        Instant expiresAt,
        CommandStatus status,
        BigDecimal appliedPowerKw,
        String rejectionReason,
        Instant requestedAt,
        Instant acknowledgedAt,
        long version
) {
}
