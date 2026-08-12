package io.voltweave.telemetry.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.contracts.events.v1.TelemetryQualityV1;
import io.voltweave.contracts.events.v1.TelemetryRawPayloadV1;
import io.voltweave.telemetry.processing.application.exception.TelemetryValidationException;
import io.voltweave.telemetry.processing.application.model.TelemetryCursor;
import tools.jackson.databind.json.JsonMapper;

class TelemetryNormalizerTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000001"
    );
    private static final UUID DEVICE_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000001"
    );
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-12T12:00:00Z");
    private static final String MQTT_TOPIC = "voltweave/%s/%s/%s/telemetry".formatted(
            ORGANIZATION_ID, SITE_ID, DEVICE_ID
    );

    private final TelemetryNormalizer normalizer = new TelemetryNormalizer(
            JsonMapper.builder().findAndAddModules().build(),
            Duration.ofMinutes(5),
            Duration.ofMinutes(2)
    );

    @Test
    void normalizesPrecisionAndMarksAFreshSampleValid() {
        var normalized = normalize(sample(7, RECEIVED_AT.minusSeconds(10), "12.34567", "60.1239"));

        assertThat(normalized.sequenceNumber()).isEqualTo(7);
        assertThat(normalized.activePowerKw()).isEqualByComparingTo("12.346");
        assertThat(normalized.socPercent()).isEqualByComparingTo("60.124");
        assertThat(normalized.quality()).isEqualTo(TelemetryQualityV1.VALID);
    }

    @Test
    void marksAnOldButOtherwiseValidSampleStale() {
        var normalized = normalize(sample(
                7, RECEIVED_AT.minus(Duration.ofMinutes(6)), "1", "50"
        ));

        assertThat(normalized.quality()).isEqualTo(TelemetryQualityV1.STALE);
    }

    @Test
    void acceptsBoundedOutOfOrderTelemetry() {
        var raw = sample(9, RECEIVED_AT.minusSeconds(60), "1", "50");
        var cursor = new TelemetryCursor(10, RECEIVED_AT.minusSeconds(30));

        var normalized = normalizer.normalize(
                ORGANIZATION_ID, raw, RECEIVED_AT, Optional.of(cursor)
        );

        assertThat(normalized.quality()).isEqualTo(TelemetryQualityV1.OUT_OF_ORDER);
    }

    @Test
    void rejectsTelemetryOutsideTheOutOfOrderWindow() {
        var raw = sample(9, RECEIVED_AT.minusSeconds(180), "1", "50");
        var cursor = new TelemetryCursor(10, RECEIVED_AT.minusSeconds(30));

        assertThatThrownBy(() -> normalizer.normalize(
                ORGANIZATION_ID, raw, RECEIVED_AT, Optional.of(cursor)
        )).isInstanceOf(TelemetryValidationException.class)
                .extracting(exception -> ((TelemetryValidationException) exception).reasonCode())
                .isEqualTo("OUT_OF_ORDER_LIMIT");
    }

    @Test
    void rejectsAnIdentityMismatch() {
        UUID otherDevice = UUID.randomUUID();
        String json = json(otherDevice, 1, RECEIVED_AT, "BATTERY", "1", "50", true);

        assertThatThrownBy(() -> normalize(raw(json)))
                .isInstanceOf(TelemetryValidationException.class)
                .extracting(exception -> ((TelemetryValidationException) exception).reasonCode())
                .isEqualTo("IDENTITY_MISMATCH");
    }

    @Test
    void rejectsSocForADeviceWithoutEnergyStorage() {
        String json = json(DEVICE_ID, 1, RECEIVED_AT, "SMART_METER", "1", "50", true);

        assertThatThrownBy(() -> normalize(raw(json)))
                .isInstanceOf(TelemetryValidationException.class)
                .extracting(exception -> ((TelemetryValidationException) exception).reasonCode())
                .isEqualTo("INVALID_SOC");
    }

    private io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1 normalize(
            TelemetryRawPayloadV1 raw
    ) {
        return normalizer.normalize(ORGANIZATION_ID, raw, RECEIVED_AT, Optional.empty());
    }

    private static TelemetryRawPayloadV1 sample(
            long sequence,
            Instant observedAt,
            String power,
            String soc
    ) {
        return raw(json(DEVICE_ID, sequence, observedAt, "BATTERY", power, soc, true));
    }

    private static TelemetryRawPayloadV1 raw(String json) {
        return new TelemetryRawPayloadV1(
                SITE_ID, DEVICE_ID, MQTT_TOPIC, 1, false,
                Base64.getEncoder().encodeToString(json.getBytes())
        );
    }

    private static String json(
            UUID deviceId,
            long sequence,
            Instant observedAt,
            String type,
            String power,
            String soc,
            boolean online
    ) {
        return """
                {
                  "deviceId":"%s",
                  "sequenceNumber":%d,
                  "observedAt":"%s",
                  "type":"%s",
                  "activePowerKw":%s,
                  "socPercent":%s,
                  "online":%s
                }
                """.formatted(deviceId, sequence, observedAt, type, power, soc, online);
    }
}
