package io.voltweave.portfolio.device.api.response;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.device.domain.entities.DeviceProvisioningRequest;
import io.voltweave.portfolio.device.application.model.DeviceProvisioningResult;
import io.voltweave.portfolio.device.domain.enums.ProvisioningStatus;

public record DeviceProvisioningResponse(
        UUID id,
        UUID deviceId,
        ProvisioningStatus status,
        String mqttUsername,
        String mqttClientId,
        Instant provisionedAt,
        Instant revokedAt,
        MqttCredentialResponse credential,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeviceProvisioningResponse from(DeviceProvisioningRequest request) {
        return new DeviceProvisioningResponse(
                request.id(), request.deviceId(), request.status(),
                request.mqttUsername(), request.mqttClientId(),
                request.provisionedAt(), request.revokedAt(), null,
                request.createdAt(), request.updatedAt()
        );
    }

    public static DeviceProvisioningResponse from(DeviceProvisioningResult result) {
        var request = result.request();
        return new DeviceProvisioningResponse(
                request.id(), request.deviceId(), request.status(),
                request.mqttUsername(), request.mqttClientId(),
                request.provisionedAt(), request.revokedAt(),
                MqttCredentialResponse.from(result.credential()),
                request.createdAt(), request.updatedAt()
        );
    }
}
