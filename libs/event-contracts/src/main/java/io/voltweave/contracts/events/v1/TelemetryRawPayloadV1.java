package io.voltweave.contracts.events.v1;

import java.util.Objects;
import java.util.UUID;

public record TelemetryRawPayloadV1(
        UUID siteId,
        UUID deviceId,
        String mqttTopic,
        int mqttQos,
        boolean retained,
        String payloadBase64
) {
    public TelemetryRawPayloadV1 {
        Objects.requireNonNull(siteId, "siteId is required");
        Objects.requireNonNull(deviceId, "deviceId is required");
        mqttTopic = requireText(mqttTopic, "mqttTopic", 512);
        payloadBase64 = requireText(payloadBase64, "payloadBase64", 1_400_000);
        if (mqttQos < 0 || mqttQos > 2) {
            throw new IllegalArgumentException("mqttQos must be between 0 and 2");
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return value;
    }
}
