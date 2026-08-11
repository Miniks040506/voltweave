package io.voltweave.portfolio.device.api.response;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.device.domain.entities.DeviceProvisioningRequest;
import io.voltweave.portfolio.device.domain.enums.ProvisioningStatus;

public record DeviceProvisioningResponse(
        UUID id,
        UUID deviceId,
        ProvisioningStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeviceProvisioningResponse from(DeviceProvisioningRequest request) {
        return new DeviceProvisioningResponse(
                request.id(), request.deviceId(), request.status(),
                request.createdAt(), request.updatedAt()
        );
    }
}
