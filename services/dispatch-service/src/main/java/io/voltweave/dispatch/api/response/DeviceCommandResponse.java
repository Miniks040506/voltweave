package io.voltweave.dispatch.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.dispatch.application.model.DeviceCommand;

public record DeviceCommandResponse(
        UUID id,
        UUID dispatchId,
        UUID siteId,
        UUID deviceId,
        String commandType,
        BigDecimal targetPowerKw,
        Instant validFrom,
        Instant acknowledgementDeadlineAt,
        Instant expiresAt,
        String status,
        BigDecimal appliedPowerKw,
        String rejectionReason,
        Instant requestedAt,
        Instant acknowledgedAt,
        long version
) {
    public static DeviceCommandResponse from(DeviceCommand command) {
        return new DeviceCommandResponse(
                command.id(), command.dispatchId(), command.siteId(), command.deviceId(),
                command.commandType(), command.targetPowerKw(), command.validFrom(),
                command.acknowledgementDeadlineAt(), command.expiresAt(),
                command.status().name(), command.appliedPowerKw(),
                command.rejectionReason(), command.requestedAt(), command.acknowledgedAt(),
                command.version()
        );
    }
}
