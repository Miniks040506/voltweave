package io.voltweave.telemetry.ingress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TelemetryTopicTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );
    private static final UUID DEVICE_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
    );

    @Test
    void parsesTenantSiteAndDeviceFromTheProvisionedTopicShape() {
        var topic = TelemetryTopic.parse(
                "voltweave/%s/%s/%s/telemetry".formatted(
                        ORGANIZATION_ID, SITE_ID, DEVICE_ID
                )
        );

        assertThat(topic.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(topic.siteId()).isEqualTo(SITE_ID);
        assertThat(topic.deviceId()).isEqualTo(DEVICE_ID);
    }

    @Test
    void rejectsTopicsOutsideTheTelemetryNamespace() {
        assertThatThrownBy(() -> TelemetryTopic.parse(
                "voltweave/%s/%s/%s/command".formatted(
                        ORGANIZATION_ID, SITE_ID, DEVICE_ID
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid telemetry MQTT topic");
    }

    @Test
    void rejectsNonUuidResourceIdentifiers() {
        assertThatThrownBy(() -> TelemetryTopic.parse(
                "voltweave/not-a-uuid/%s/%s/telemetry".formatted(SITE_ID, DEVICE_ID)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Telemetry topic identifiers must be UUIDs");
    }
}
