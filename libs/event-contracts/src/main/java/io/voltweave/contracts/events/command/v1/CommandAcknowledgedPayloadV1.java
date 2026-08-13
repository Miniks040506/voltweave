package io.voltweave.contracts.events.command.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CommandAcknowledgedPayloadV1(
        UUID commandId,
        UUID dispatchId,
        UUID siteId,
        UUID deviceId,
        String status,
        BigDecimal appliedPowerKw,
        String reason,
        Instant processedAt
) {
    public CommandAcknowledgedPayloadV1 {
        Objects.requireNonNull(commandId, "commandId is required");
        Objects.requireNonNull(dispatchId, "dispatchId is required");
        Objects.requireNonNull(siteId, "siteId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(appliedPowerKw, "appliedPowerKw is required");
        Objects.requireNonNull(processedAt, "processedAt is required");
        status = requireStatus(status);
        reason = reason == null || reason.isBlank() ? null : reason.trim();
        if ("REJECTED".equals(status) && reason == null) {
            throw new IllegalArgumentException("Rejected acknowledgement requires a reason");
        }
        if (reason != null && reason.length() > 500) {
            throw new IllegalArgumentException("reason exceeds 500 characters");
        }
    }

    private static String requireStatus(String value) {
        if (!"ACCEPTED".equals(value) && !"REJECTED".equals(value)) {
            throw new IllegalArgumentException("status must be ACCEPTED or REJECTED");
        }
        return value;
    }
}
