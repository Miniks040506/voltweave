package io.voltweave.contracts.events.command.v1;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record CommandRequestedPayloadV1(
        UUID commandId,
        UUID dispatchId,
        UUID siteId,
        UUID deviceId,
        String commandType,
        BigDecimal targetPowerKw,
        Instant validFrom,
        Instant acknowledgementDeadlineAt,
        Instant expiresAt,
        UUID supersedesCommandId
) {
    public CommandRequestedPayloadV1 {
        Objects.requireNonNull(commandId, "commandId is required");
        Objects.requireNonNull(dispatchId, "dispatchId is required");
        Objects.requireNonNull(siteId, "siteId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(targetPowerKw, "targetPowerKw is required");
        Objects.requireNonNull(validFrom, "validFrom is required");
        Objects.requireNonNull(
                acknowledgementDeadlineAt, "acknowledgementDeadlineAt is required"
        );
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        commandType = requireText(commandType, "commandType", 32);
        if (!validFrom.isBefore(expiresAt)) {
            throw new IllegalArgumentException("validFrom must precede expiresAt");
        }
        if (acknowledgementDeadlineAt.isBefore(validFrom)
                || acknowledgementDeadlineAt.isAfter(expiresAt)) {
            throw new IllegalArgumentException(
                    "acknowledgementDeadlineAt must be within the command interval"
            );
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
