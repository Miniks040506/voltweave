package io.voltweave.simulator.config;

public record MqttCredential(
        String username,
        String password,
        String clientId,
        String telemetryTopic,
        String statusTopic,
        String acknowledgementTopic,
        String commandTopic
) {
    public MqttCredential {
        username = requireText(username, "username");
        password = requireText(password, "password");
        clientId = requireText(clientId, "clientId");
        telemetryTopic = requireText(telemetryTopic, "telemetryTopic");
        statusTopic = requireText(statusTopic, "statusTopic");
        acknowledgementTopic = requireText(acknowledgementTopic, "acknowledgementTopic");
        commandTopic = requireText(commandTopic, "commandTopic");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
