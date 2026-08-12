package io.voltweave.telemetry.processing.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.contracts.events.v1.TelemetryRawPayloadV1;
import io.voltweave.telemetry.TimescaleTestConfiguration;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "voltweave.ingress.enabled=false",
        "voltweave.processing.enabled=true",
        "spring.kafka.listener.auto-startup=false",
        "voltweave.processing.outbox-poll-delay=1h",
        "voltweave.processing.dedup-cleanup-delay=1h"
})
@Import(TimescaleTestConfiguration.class)
class RawTelemetryConsumerIntegrationTests {
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

    @Autowired
    private RawTelemetryConsumer consumer;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearProcessingState() {
        jdbcClient.sql("""
                TRUNCATE event_inbox, event_outbox, telemetry_dedup, quarantined_telemetry
                """).update();
    }

    @Test
    void validRawEventAtomicallyCreatesInboxDedupAndNormalizedOutbox() throws Exception {
        UUID rawEventId = UUID.randomUUID();

        consumer.consume(record(rawEventId, 7, validSample(7, "60.1239"), 0));

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("telemetry_dedup")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
        assertThat(count("quarantined_telemetry")).isZero();

        String outbox = jdbcClient.sql("SELECT payload::text FROM event_outbox")
                .query(String.class).single();
        var normalized = objectMapper.readTree(outbox);
        assertThat(normalized.path("eventType").asString())
                .isEqualTo(EventTypes.TELEMETRY_NORMALIZED);
        assertThat(normalized.path("causationId").asString()).isEqualTo(rawEventId.toString());
        assertThat(normalized.path("partitionKey").asString()).isEqualTo(DEVICE_ID.toString());
        assertThat(normalized.path("payload").path("activePowerKw").decimalValue())
                .isEqualByComparingTo("12.346");
        assertThat(normalized.path("payload").path("quality").asString()).isEqualTo("VALID");
    }

    @Test
    void replayedEventIdHasNoSecondSideEffect() {
        UUID eventId = UUID.randomUUID();
        var record = record(eventId, 7, validSample(7, "60"), 1);

        consumer.consume(record);
        consumer.consume(record);

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("telemetry_dedup")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
    }

    @Test
    void duplicateDeviceSequenceRecordsBothEventsButPublishesOnce() {
        consumer.consume(record(UUID.randomUUID(), 7, validSample(7, "60"), 2));
        consumer.consume(record(UUID.randomUUID(), 7, validSample(7, "61"), 3));

        assertThat(count("event_inbox")).isEqualTo(2);
        assertThat(count("telemetry_dedup")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
        assertThat(count("quarantined_telemetry")).isZero();
    }

    @Test
    void invalidSampleIsQuarantinedWithoutDedupOrOutbox() {
        consumer.consume(record(UUID.randomUUID(), 8, validSample(8, "101"), 4));

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("telemetry_dedup")).isZero();
        assertThat(count("event_outbox")).isZero();
        assertThat(count("quarantined_telemetry")).isEqualTo(1);
        assertThat(single("SELECT reason_code FROM quarantined_telemetry"))
                .isEqualTo("INVALID_SOC");
    }

    @Test
    void malformedKafkaValueUsesOffsetBasedQuarantineIdentity() {
        var malformed = new ConsumerRecord<String, String>(
                EventTopics.TELEMETRY_RAW_V1, 1, 42L, DEVICE_ID.toString(), "not-json"
        );

        consumer.consume(malformed);
        consumer.consume(malformed);

        assertThat(count("event_inbox")).isZero();
        assertThat(count("quarantined_telemetry")).isEqualTo(1);
        assertThat(single("SELECT reason_code FROM quarantined_telemetry"))
                .isEqualTo("INVALID_RAW_EVENT");
    }

    private ConsumerRecord<String, String> record(
            UUID eventId,
            long sequence,
            String sample,
            long offset
    ) {
        try {
            var raw = new TelemetryRawPayloadV1(
                    SITE_ID, DEVICE_ID, MQTT_TOPIC, 1, false,
                    Base64.getEncoder().encodeToString(sample.getBytes())
            );
            var envelope = new EventEnvelopeV1<>(
                    eventId, EventTypes.TELEMETRY_RAW_RECEIVED, 1, RECEIVED_AT,
                    "telemetry-service", ORGANIZATION_ID, UUID.randomUUID(), null,
                    DEVICE_ID.toString(), raw
            );
            return new ConsumerRecord<>(
                    EventTopics.TELEMETRY_RAW_V1, 0, offset,
                    DEVICE_ID.toString(), objectMapper.writeValueAsString(envelope)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String validSample(long sequence, String soc) {
        return """
                {
                  "deviceId":"%s",
                  "sequenceNumber":%d,
                  "observedAt":"2026-08-12T11:59:50Z",
                  "type":"BATTERY",
                  "activePowerKw":12.34567,
                  "socPercent":%s,
                  "online":true
                }
                """.formatted(DEVICE_ID, sequence, soc);
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private String single(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }
}
