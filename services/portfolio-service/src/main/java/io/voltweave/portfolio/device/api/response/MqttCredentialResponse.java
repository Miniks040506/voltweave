package io.voltweave.portfolio.device.api.response;

import io.voltweave.portfolio.device.application.model.MqttDeviceCredential;

public record MqttCredentialResponse(
        String brokerUri,
        String username,
        String password,
        String clientId,
        String telemetryTopic,
        String statusTopic,
        String acknowledgementTopic,
        String commandTopic
) {
    public static MqttCredentialResponse from(MqttDeviceCredential credential) {
        return credential == null ? null : new MqttCredentialResponse(
                credential.brokerUri(), credential.username(), credential.password(),
                credential.clientId(), credential.telemetryTopic(), credential.statusTopic(),
                credential.acknowledgementTopic(), credential.commandTopic()
        );
    }
}
