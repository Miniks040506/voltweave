package io.voltweave.portfolio.device.application.exception;

import java.util.UUID;

public class DeviceProvisioningConflictException extends RuntimeException {
    public DeviceProvisioningConflictException(UUID deviceId) {
        super("Device cannot begin provisioning: " + deviceId);
    }
}
