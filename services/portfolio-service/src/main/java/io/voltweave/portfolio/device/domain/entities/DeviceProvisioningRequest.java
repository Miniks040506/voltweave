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
        String mqttUsername,
        String mqttClientId,
        Instant provisionedAt,
        Instant revokedAt,
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
        if (status == ProvisioningStatus.PENDING) {
            if (mqttUsername != null || mqttClientId != null
                    || provisionedAt != null || revokedAt != null) {
                throw new IllegalArgumentException("Pending request cannot contain credentials");
            }
        } else {
            mqttUsername = requireText(mqttUsername, "mqttUsername");
            mqttClientId = requireText(mqttClientId, "mqttClientId");
            Objects.requireNonNull(provisionedAt, "provisionedAt is required");
            if (provisionedAt.isBefore(createdAt)) {
                throw new IllegalArgumentException("provisionedAt cannot be before createdAt");
            }
            if (status == ProvisioningStatus.REVOKED && revokedAt == null) {
                throw new IllegalArgumentException("revokedAt is required");
            }
            if (status == ProvisioningStatus.PROVISIONED && revokedAt != null) {
                throw new IllegalArgumentException("Provisioned request cannot be revoked");
            }
        }
    }

    public static DeviceProvisioningRequest pending(Device device, Instant now) {
        return new DeviceProvisioningRequest(
                UUID.randomUUID(), device.organizationId(), device.id(),
                ProvisioningStatus.PENDING, null, null, null, null, now, now
        );
    }

    public DeviceProvisioningRequest complete(
            String mqttUsername,
            String mqttClientId,
            Instant now
    ) {
        if (status != ProvisioningStatus.PENDING) {
            throw new IllegalStateException("Only a pending request can be completed");
        }
        return new DeviceProvisioningRequest(
                id, organizationId, deviceId, ProvisioningStatus.PROVISIONED,
                mqttUsername, mqttClientId, now, null, createdAt, now
        );
    }

    public DeviceProvisioningRequest revoke(Instant now) {
        if (status != ProvisioningStatus.PROVISIONED) {
            throw new IllegalStateException("Only a provisioned request can be revoked");
        }
        return new DeviceProvisioningRequest(
                id, organizationId, deviceId, ProvisioningStatus.REVOKED,
                mqttUsername, mqttClientId, provisionedAt, now, createdAt, now
        );
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        var normalized = value.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException(field + " exceeds 128 characters");
        }
        return normalized;
    }
}
