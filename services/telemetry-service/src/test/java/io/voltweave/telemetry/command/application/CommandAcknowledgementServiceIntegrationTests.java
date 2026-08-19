package io.voltweave.telemetry.command.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import io.voltweave.contracts.events.EventTypes;
import io.voltweave.telemetry.TimescaleTestConfiguration;
import io.voltweave.telemetry.command.persistence.CommandGatewayRepository;

@SpringBootTest(properties = {
        "voltweave.ingress.enabled=false",
        "voltweave.processing.enabled=false",
        "voltweave.command.enabled=false"
})
@Import(TimescaleTestConfiguration.class)
class CommandAcknowledgementServiceIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DISPATCH_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final UUID COMMAND_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final UUID REQUEST_EVENT_ID = UUID.randomUUID();

    @Autowired
    private CommandAcknowledgementService service;

    @Autowired
    private CommandGatewayRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void seedDelivery() {
        jdbcClient.sql("TRUNCATE command_deliveries, event_outbox").update();
        jdbcClient.sql("""
                INSERT INTO command_deliveries (
                    command_id, organization_id, dispatch_id, site_id, device_id,
                    correlation_id, requested_event_id, mqtt_topic, mqtt_payload,
                    valid_from, acknowledgement_deadline_at, expires_at,
                    received_at, status, published_at
                ) VALUES (
                    :commandId, :organizationId, :dispatchId, :siteId, :deviceId,
                    :correlationId, :requestedEventId, 'command', '{}'::jsonb,
                    now() - interval '1 minute', now() + interval '30 seconds',
                    now() + interval '5 minutes',
                    now(), 'PUBLISHED', now()
                )
                """)
                .param("commandId", COMMAND_ID)
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", DISPATCH_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", DEVICE_ID)
                .param("correlationId", CORRELATION_ID)
                .param("requestedEventId", REQUEST_EVENT_ID)
                .update();
    }

    @Test
    void acknowledgementAndKafkaOutboxAreAtomicAndIdempotent() {
        byte[] ack = acceptedAck();

        service.receive(topic(DEVICE_ID), ack);
        service.receive(topic(DEVICE_ID), ack);

        assertThat(value("SELECT status FROM command_deliveries"))
                .isEqualTo("ACKNOWLEDGED");
        assertThat(count("event_outbox")).isEqualTo(1);
        String payload = value("SELECT payload::text FROM event_outbox");
        assertThat(payload).contains(EventTypes.COMMAND_ACKNOWLEDGED)
                .contains(CORRELATION_ID.toString())
                .contains(REQUEST_EVENT_ID.toString())
                .contains(DISPATCH_ID.toString());
    }

    @Test
    void rejectsAcknowledgementFromAnotherDeviceTopic() {
        assertThatThrownBy(() -> service.receive(topic(UUID.randomUUID()), acceptedAck()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Acknowledgement topic does not own command");

        assertThat(count("event_outbox")).isZero();
        assertThat(value("SELECT status FROM command_deliveries")).isEqualTo("PUBLISHED");
    }

    @Test
    void persistsTimeoutAndDoesNotPublishALateAcknowledgement() {
        expireDelivery();

        service.receive(topic(DEVICE_ID), acceptedAck());

        assertThat(value("SELECT status FROM command_deliveries")).isEqualTo("TIMED_OUT");
        assertThat(value("SELECT last_error FROM command_deliveries"))
                .isEqualTo("ACKNOWLEDGEMENT_TIMEOUT");
        assertThat(count("event_outbox")).isZero();
    }

    @Test
    void persistsTimeoutWithoutADeviceAcknowledgement() {
        expireDelivery();

        repository.timeOutExpired(Instant.now(), 50);

        assertThat(value("SELECT status FROM command_deliveries")).isEqualTo("TIMED_OUT");
        assertThat(count("event_outbox")).isZero();
    }

    private void expireDelivery() {
        jdbcClient.sql("""
                UPDATE command_deliveries
                SET acknowledgement_deadline_at = now() - interval '1 second'
                """).update();
    }

    private static byte[] acceptedAck() {
        return ("""
                {"commandId":"%s","status":"ACCEPTED","appliedPowerKw":-12.5,
                 "reason":null,"processedAt":"2026-08-13T10:00:01Z"}
                """).formatted(COMMAND_ID).getBytes();
    }

    private static String topic(UUID deviceId) {
        return "voltweave/%s/%s/%s/ack".formatted(ORGANIZATION_ID, SITE_ID, deviceId);
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private String value(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }
}
