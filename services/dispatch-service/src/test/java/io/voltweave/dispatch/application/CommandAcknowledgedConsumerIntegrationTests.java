package io.voltweave.dispatch.application;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.command.v1.CommandAcknowledgedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.dispatch.PostgresTestConfiguration;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresTestConfiguration.class)
@Transactional
class CommandAcknowledgedConsumerIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DISPATCH_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID FIRST_DEVICE_ID = UUID.randomUUID();
    private static final UUID SECOND_DEVICE_ID = UUID.randomUUID();
    private static final UUID FIRST_COMMAND_ID = UUID.randomUUID();
    private static final UUID SECOND_COMMAND_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");

    @Autowired
    private CommandAcknowledgedConsumer consumer;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedPreparingDispatch() {
        jdbcClient.sql("""
                INSERT INTO dispatches (
                    id, organization_id, vpp_id, optimization_preview_id,
                    optimization_preview_version, type, target_power_kw,
                    required_power_kw, planned_power_kw, scheduled_start_at,
                    scheduled_end_at, status, created_by, created_at
                ) VALUES (
                    :id, :organizationId, :vppId, :previewId, 1,
                    'REDUCE_DEMAND', 10, 10, 10, :startAt, :endAt,
                    'PREPARING', 'operator', :createdAt
                )
                """)
                .param("id", DISPATCH_ID)
                .param("organizationId", ORGANIZATION_ID)
                .param("vppId", UUID.randomUUID())
                .param("previewId", UUID.randomUUID())
                .param("startAt", java.sql.Timestamp.from(NOW))
                .param("endAt", java.sql.Timestamp.from(NOW.plusSeconds(900)))
                .param("createdAt", java.sql.Timestamp.from(NOW.minusSeconds(60)))
                .update();
        insertCommand(FIRST_COMMAND_ID, FIRST_DEVICE_ID);
        insertCommand(SECOND_COMMAND_ID, SECOND_DEVICE_ID);
    }

    @Test
    void activatesDispatchOnlyAfterEveryCommandIsAcceptedAndIgnoresReplay() throws Exception {
        ConsumerRecord<String, String> first = accepted(
                UUID.randomUUID(), FIRST_COMMAND_ID, FIRST_DEVICE_ID
        );
        consumer.consume(first);
        consumer.consume(first);

        assertThat(commandStatus(FIRST_COMMAND_ID)).isEqualTo("ACCEPTED");
        assertThat(dispatchStatus()).isEqualTo("PREPARING");
        assertThat(count("event_inbox")).isEqualTo(1);

        consumer.consume(accepted(UUID.randomUUID(), SECOND_COMMAND_ID, SECOND_DEVICE_ID));

        assertThat(commandStatus(SECOND_COMMAND_ID)).isEqualTo("ACCEPTED");
        assertThat(dispatchStatus()).isEqualTo("ACTIVE");
        assertThat(count("event_inbox")).isEqualTo(2);
    }

    @Test
    void rejectsAnAcknowledgementClaimingAnotherDevice() throws Exception {
        var invalid = accepted(UUID.randomUUID(), FIRST_COMMAND_ID, SECOND_DEVICE_ID);

        assertThatThrownBy(() -> consumer.consume(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Acknowledgement does not own command");

        assertThat(commandStatus(FIRST_COMMAND_ID)).isEqualTo("REQUESTED");
    }

    private ConsumerRecord<String, String> accepted(
            UUID eventId,
            UUID commandId,
            UUID deviceId
    ) throws Exception {
        var payload = new CommandAcknowledgedPayloadV1(
                commandId, DISPATCH_ID, SITE_ID, deviceId, "ACCEPTED",
                new BigDecimal("-5.000"), null, NOW.plusSeconds(1)
        );
        var event = new EventEnvelopeV1<>(
                eventId, EventTypes.COMMAND_ACKNOWLEDGED, 1, NOW.plusSeconds(2),
                "telemetry-service", ORGANIZATION_ID, UUID.randomUUID(),
                UUID.randomUUID(), deviceId.toString(), payload
        );
        return new ConsumerRecord<>(
                EventTopics.COMMAND_LIFECYCLE_V1, 0, 0,
                deviceId.toString(), objectMapper.writeValueAsString(event)
        );
    }

    private void insertCommand(UUID commandId, UUID deviceId) {
        jdbcClient.sql("""
                INSERT INTO device_commands (
                    id, organization_id, dispatch_id, site_id, device_id,
                    command_type, target_power_kw, valid_from, expires_at,
                    status, requested_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :siteId, :deviceId,
                    'SET_POWER', -5, :validFrom, :expiresAt, 'REQUESTED', :requestedAt
                )
                """)
                .param("id", commandId)
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", DISPATCH_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", deviceId)
                .param("validFrom", java.sql.Timestamp.from(NOW))
                .param("expiresAt", java.sql.Timestamp.from(NOW.plusSeconds(900)))
                .param("requestedAt", java.sql.Timestamp.from(NOW.minusSeconds(60)))
                .update();
    }

    private String commandStatus(UUID commandId) {
        return jdbcClient.sql("SELECT status FROM device_commands WHERE id = :id")
                .param("id", commandId).query(String.class).single();
    }

    private String dispatchStatus() {
        return jdbcClient.sql("SELECT status FROM dispatches WHERE id = :id")
                .param("id", DISPATCH_ID).query(String.class).single();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
