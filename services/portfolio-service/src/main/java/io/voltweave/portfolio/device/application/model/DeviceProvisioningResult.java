package io.voltweave.portfolio.device.application.model;

import io.voltweave.portfolio.device.domain.entities.DeviceProvisioningRequest;

public record DeviceProvisioningResult(
        DeviceProvisioningRequest request,
        MqttDeviceCredential credential
) {
    public boolean containsNewCredential() {
        return credential != null;
    }
}
