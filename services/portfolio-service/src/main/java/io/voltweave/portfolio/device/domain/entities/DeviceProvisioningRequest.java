package io.voltweave.portfolio.device.domain.entities;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.portfolio.device.domain.enums.ProvisioningStatus;

public record DeviceProvisioningRequest(
        UUID id,
        UUID organizationId,
        UUID deviceId,
        ProvisioningStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public DeviceProvisioningRequest {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public static DeviceProvisioningRequest pending(Device device, Instant now) {
        return new DeviceProvisioningRequest(
                UUID.randomUUID(), device.organizationId(), device.id(),
                ProvisioningStatus.PENDING, now, now
        );
    }
}
