package io.voltweave.contracts.events.v1;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TelemetryNormalizedPayloadV1Tests {
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void rejectsAnInvalidSoc() {
        assertThatThrownBy(() -> payload(BigDecimal.valueOf(101), OBSERVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("socPercent must be between 0 and 100");
    }

    @Test
    void rejectsADeviceTimestampAfterReceipt() {
        assertThatThrownBy(() -> payload(
                BigDecimal.valueOf(50), OBSERVED_AT.plusSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("receivedAt must not precede observedAt");
    }

    private static TelemetryNormalizedPayloadV1 payload(
            BigDecimal socPercent,
            Instant observedAt
    ) {
        return new TelemetryNormalizedPayloadV1(
                SITE_ID, DEVICE_ID, 1, observedAt, OBSERVED_AT,
                "BATTERY", BigDecimal.ONE, socPercent, true,
                TelemetryQualityV1.VALID
        );
    }
}
