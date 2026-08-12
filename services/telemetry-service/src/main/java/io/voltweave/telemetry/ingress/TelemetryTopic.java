package io.voltweave.telemetry.ingress;

import java.util.UUID;

public record TelemetryTopic(UUID organizationId, UUID siteId, UUID deviceId) {
    private static final int SEGMENT_COUNT = 5;

    public static TelemetryTopic parse(String topic) {
        String[] segments = topic.split("/", -1);
        if (segments.length != SEGMENT_COUNT
                || !"voltweave".equals(segments[0])
                || !"telemetry".equals(segments[4])) {
            throw new IllegalArgumentException("Invalid telemetry MQTT topic");
        }
        try {
            return new TelemetryTopic(
                    UUID.fromString(segments[1]),
                    UUID.fromString(segments[2]),
                    UUID.fromString(segments[3])
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Telemetry topic identifiers must be UUIDs", exception);
        }
    }
}
