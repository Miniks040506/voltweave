package io.voltweave.portfolio.device.application.model;

public record MqttDeviceCredential(
        String brokerUri,
        String username,
        String password,
        String clientId,
        String telemetryTopic,
        String statusTopic,
        String acknowledgementTopic,
        String commandTopic
) {
}
