package io.voltweave.telemetry.processing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.contracts.events.v1.TelemetryQualityV1;
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
class NormalizedTelemetryConsumerIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000017"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000017"
    );
    private static final UUID DEVICE_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000017"
    );
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-12T12:00:00Z");

    @Autowired
    private NormalizedTelemetryConsumer consumer;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearAcceptedStorage() {
        jdbcClient.sql("""
                TRUNCATE telemetry_points, device_twins, event_inbox
                """).update();
    }

    @Test
    void acceptedEventAtomicallyCreatesInboxPointAndTwin() {
        consumer.consume(record(
                UUID.randomUUID(), 10, "2026-08-12T11:59:50Z",
                "12.345", "60.125", TelemetryQualityV1.VALID, 0
        ));

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("telemetry_points")).isEqualTo(1);
        assertThat(count("device_twins")).isEqualTo(1);
        assertThat(single("SELECT telemetry_quality FROM telemetry_points"))
                .isEqualTo("VALID");
        assertThat(single("SELECT last_sequence_number::text FROM device_twins"))
                .isEqualTo("10");
        assertThat(single("SELECT active_power_kw::text FROM device_twins"))
                .isEqualTo("12.345");
    }

    @Test
    void replayedEventDoesNotDuplicateAcceptedState() {
        UUID eventId = UUID.randomUUID();
        var record = record(
                eventId, 10, "2026-08-12T11:59:50Z",
                "12", "60", TelemetryQualityV1.VALID, 1
        );

        consumer.consume(record);
        consumer.consume(record);

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("telemetry_points")).isEqualTo(1);
        assertThat(count("device_twins")).isEqualTo(1);
    }

    @Test
    void storesOutOfOrderPointWithoutRegressingTwin() {
        consumer.consume(record(
                UUID.randomUUID(), 10, "2026-08-12T11:59:50Z",
                "10", "60", TelemetryQualityV1.VALID, 2
        ));
        consumer.consume(record(
                UUID.randomUUID(), 11, "2026-08-12T11:59:55Z",
                "11", "61", TelemetryQualityV1.STALE, 3
        ));
        consumer.consume(record(
                UUID.randomUUID(), 9, "2026-08-12T11:59:45Z",
                "9", "59", TelemetryQualityV1.OUT_OF_ORDER, 4
        ));

        assertThat(count("telemetry_points")).isEqualTo(3);
        assertThat(single("SELECT last_sequence_number::text FROM device_twins"))
                .isEqualTo("11");
        assertThat(single("SELECT active_power_kw::text FROM device_twins"))
                .isEqualTo("11.000");
        assertThat(single("SELECT telemetry_quality FROM device_twins"))
                .isEqualTo("STALE");
    }

    @Test
    void duplicatePointWithANewEventIdHasNoSecondTwinMutation() {
        consumer.consume(record(
                UUID.randomUUID(), 10, "2026-08-12T11:59:50Z",
                "10", "60", TelemetryQualityV1.VALID, 5
        ));
        consumer.consume(record(
                UUID.randomUUID(), 10, "2026-08-12T11:59:50Z",
                "99", "70", TelemetryQualityV1.VALID, 6
        ));

        assertThat(count("event_inbox")).isEqualTo(2);
        assertThat(count("telemetry_points")).isEqualTo(1);
        assertThat(single("SELECT active_power_kw::text FROM device_twins"))
                .isEqualTo("10.000");
    }

    @Test
    void databaseFailureRollsBackInbox() {
        var oversizedPower = record(
                UUID.randomUUID(), 10, "2026-08-12T11:59:50Z",
                "1000000000", "60", TelemetryQualityV1.VALID, 7
        );

        assertThatThrownBy(() -> consumer.consume(oversizedPower))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(count("event_inbox")).isZero();
        assertThat(count("telemetry_points")).isZero();
        assertThat(count("device_twins")).isZero();
    }

    @Test
    void rejectsNonAcceptedQualityBeforeWritingInbox() {
        var invalid = record(
                UUID.randomUUID(), 10, "2026-08-12T11:59:50Z",
                "10", "60", TelemetryQualityV1.INVALID, 8
        );

        assertThatThrownBy(() -> consumer.consume(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Normalized telemetry is not accepted storage data");
        assertThat(count("event_inbox")).isZero();
    }

    private ConsumerRecord<String, String> record(
            UUID eventId,
            long sequence,
            String observedAt,
            String activePowerKw,
            String socPercent,
            TelemetryQualityV1 quality,
            long offset
    ) {
        try {
            var telemetry = new TelemetryNormalizedPayloadV1(
                    SITE_ID, DEVICE_ID, sequence, Instant.parse(observedAt), RECEIVED_AT,
                    "BATTERY", new BigDecimal(activePowerKw), new BigDecimal(socPercent),
                    true, quality
            );
            var envelope = new EventEnvelopeV1<>(
                    eventId, EventTypes.TELEMETRY_NORMALIZED, 1, RECEIVED_AT,
                    "telemetry-service", ORGANIZATION_ID, UUID.randomUUID(),
                    UUID.randomUUID(), DEVICE_ID.toString(), telemetry
            );
            return new ConsumerRecord<>(
                    EventTopics.TELEMETRY_NORMALIZED_V1, 0, offset,
                    DEVICE_ID.toString(), objectMapper.writeValueAsString(envelope)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private String single(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }
}
