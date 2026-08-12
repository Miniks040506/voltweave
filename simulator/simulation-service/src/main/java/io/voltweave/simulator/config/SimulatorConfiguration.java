package io.voltweave.simulator.config;

import java.util.List;

public record SimulatorConfiguration(
        String brokerUri,
        int telemetryIntervalSeconds,
        List<DeviceScenario> devices
) {
    public SimulatorConfiguration {
        if (brokerUri == null || brokerUri.isBlank()) {
            throw new IllegalArgumentException("brokerUri is required");
        }
        brokerUri = brokerUri.trim();
        if (telemetryIntervalSeconds < 1) {
            throw new IllegalArgumentException("telemetryIntervalSeconds must be positive");
        }
        devices = List.copyOf(devices == null ? List.of() : devices);
        if (devices.isEmpty()) {
            throw new IllegalArgumentException("at least one device is required");
        }
        if (devices.stream().map(DeviceScenario::deviceId).distinct().count() != devices.size()) {
            throw new IllegalArgumentException("deviceId must be unique");
        }
        if (devices.stream().map(device -> device.mqtt().clientId()).distinct().count()
                != devices.size()) {
            throw new IllegalArgumentException("MQTT clientId must be unique");
        }
    }
}
