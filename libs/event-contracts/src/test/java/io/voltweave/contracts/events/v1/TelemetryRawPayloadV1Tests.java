package io.voltweave.contracts.events.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TelemetryRawPayloadV1Tests {
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );
    private static final UUID DEVICE_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
    );

    @Test
    void preservesArbitraryMqttBytesAsBase64() {
        byte[] raw = {0, 1, -1, 42};

        var payload = new TelemetryRawPayloadV1(
                SITE_ID,
                DEVICE_ID,
                "voltweave/org/site/device/telemetry",
                1,
                false,
                Base64.getEncoder().encodeToString(raw)
        );

        assertThat(Base64.getDecoder().decode(payload.payloadBase64())).containsExactly(raw);
    }

    @Test
    void rejectsAnUnsupportedMqttQos() {
        assertThatThrownBy(() -> new TelemetryRawPayloadV1(
                SITE_ID,
                DEVICE_ID,
                "voltweave/org/site/device/telemetry",
                3,
                false,
                "e30="
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("mqttQos must be between 0 and 2");
    }
}
