package io.voltweave.portfolio.device.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.portfolio.device.domain.enums.CommunicationProtocol;
import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.domain.enums.DeviceType;

public record Device(
        UUID id,
        UUID organizationId,
        UUID siteId,
        String externalDeviceId,
        DeviceType type,
        String manufacturer,
        String model,
        BigDecimal ratedPowerKw,
        DeviceLifecycleStatus status,
        CommunicationProtocol communicationProtocol,
        Instant createdAt,
        Instant updatedAt
) {
    public Device {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(siteId, "siteId is required");
        externalDeviceId = requireText(externalDeviceId, "externalDeviceId", 128);
        Objects.requireNonNull(type, "type is required");
        manufacturer = requireText(manufacturer, "manufacturer", 100);
        model = requireText(model, "model", 100);
        requirePositive(ratedPowerKw, "ratedPowerKw");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(communicationProtocol, "communicationProtocol is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public static Device registered(
            UUID organizationId,
            UUID siteId,
            String externalDeviceId,
            DeviceType type,
            String manufacturer,
            String model,
            BigDecimal ratedPowerKw,
            Instant now
    ) {
        return new Device(
                UUID.randomUUID(), organizationId, siteId, externalDeviceId, type,
                manufacturer, model, ratedPowerKw, DeviceLifecycleStatus.REGISTERED,
                CommunicationProtocol.MQTT, now, now
        );
    }

    public Device beginProvisioning(Instant now) {
        if (status != DeviceLifecycleStatus.REGISTERED) {
            throw new IllegalStateException("Only a registered device can begin provisioning");
        }
        return new Device(
                id, organizationId, siteId, externalDeviceId, type, manufacturer, model,
                ratedPowerKw, DeviceLifecycleStatus.PROVISIONING,
                communicationProtocol, createdAt, now
        );
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        var trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
