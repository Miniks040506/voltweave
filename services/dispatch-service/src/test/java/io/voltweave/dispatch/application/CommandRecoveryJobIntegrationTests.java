package io.voltweave.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.dispatch.PostgresTestConfiguration;
import io.voltweave.dispatch.persistence.CommandRepository;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "voltweave.command.recovery-poll-delay=1h"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class CommandRecoveryJobIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final UUID TIMED_OUT_DISPATCH = UUID.randomUUID();
    private static final UUID REJECTED_DISPATCH = UUID.randomUUID();
    private static final UUID HEALTHY_DISPATCH = UUID.randomUUID();

    @Autowired
    private CommandRepository repository;

    @Autowired
    private JdbcClient jdbcClient;

    private CommandRecoveryJob job;

    @BeforeEach
    void seedStalledWorkflows() {
        job = new CommandRecoveryJob(repository, Clock.fixed(NOW, ZoneOffset.UTC));
        insertDispatch(TIMED_OUT_DISPATCH);
        insertDispatch(REJECTED_DISPATCH);
        insertDispatch(HEALTHY_DISPATCH);
        insertCommand(TIMED_OUT_DISPATCH, "REQUESTED", NOW.minusSeconds(1));
        insertCommand(REJECTED_DISPATCH, "REJECTED", NOW.plusSeconds(30));
        insertCommand(HEALTHY_DISPATCH, "REQUESTED", NOW.plusSeconds(30));
    }

    @Test
    void recoversDurableStalledWorkflowsAndIsSafeToRunAgainAfterRestart() {
        job.recoverStalled();
        new CommandRecoveryJob(repository, Clock.fixed(NOW, ZoneOffset.UTC)).recoverStalled();

        assertThat(dispatchStatus(TIMED_OUT_DISPATCH)).isEqualTo("FAILED");
        assertThat(commandStatus(TIMED_OUT_DISPATCH)).isEqualTo("TIMED_OUT");
        assertThat(commandReason(TIMED_OUT_DISPATCH))
                .isEqualTo("ACKNOWLEDGEMENT_TIMEOUT");
        assertThat(dispatchStatus(REJECTED_DISPATCH)).isEqualTo("FAILED");
        assertThat(commandStatus(REJECTED_DISPATCH)).isEqualTo("REJECTED");
        assertThat(dispatchStatus(HEALTHY_DISPATCH)).isEqualTo("PREPARING");
        assertThat(commandStatus(HEALTHY_DISPATCH)).isEqualTo("REQUESTED");
    }

    private void insertDispatch(UUID dispatchId) {
        jdbcClient.sql("""
                INSERT INTO dispatches (
                    id, organization_id, vpp_id, optimization_preview_id,
                    optimization_preview_version, type, target_power_kw,
                    required_power_kw, planned_power_kw, scheduled_start_at,
                    scheduled_end_at, status, created_by, created_at
                ) VALUES (
                    :id, :organizationId, :vppId, :previewId, 1,
                    'REDUCE_DEMAND', 5, 5, 5, :startAt, :endAt,
                    'PREPARING', 'operator', :createdAt
                )
                """)
                .param("id", dispatchId)
                .param("organizationId", UUID.randomUUID())
                .param("vppId", UUID.randomUUID())
                .param("previewId", UUID.randomUUID())
                .param("startAt", timestamp(NOW.minusSeconds(60)))
                .param("endAt", timestamp(NOW.plusSeconds(900)))
                .param("createdAt", timestamp(NOW.minusSeconds(120)))
                .update();
    }

    private void insertCommand(UUID dispatchId, String status, Instant deadline) {
        boolean rejected = "REJECTED".equals(status);
        UUID organizationId = jdbcClient.sql(
                "SELECT organization_id FROM dispatches WHERE id = :id"
        ).param("id", dispatchId).query(UUID.class).single();
        jdbcClient.sql("""
                INSERT INTO device_commands (
                    id, organization_id, dispatch_id, site_id, device_id,
                    command_type, target_power_kw, valid_from,
                    acknowledgement_deadline_at, expires_at, status,
                    applied_power_kw, rejection_reason, requested_at, acknowledged_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :siteId, :deviceId,
                    'SET_POWER', -5, :validFrom, :deadline, :expiresAt, :status,
                    :appliedPower, :reason, :requestedAt, :acknowledgedAt
                )
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .param("siteId", UUID.randomUUID())
                .param("deviceId", UUID.randomUUID())
                .param("validFrom", timestamp(NOW.minusSeconds(60)))
                .param("deadline", timestamp(deadline))
                .param("expiresAt", timestamp(NOW.plusSeconds(900)))
                .param("status", status)
                .param("appliedPower", rejected ? -5 : null)
                .param("reason", rejected ? "DEVICE_REJECTED" : null)
                .param("requestedAt", timestamp(NOW.minusSeconds(120)))
                .param("acknowledgedAt", rejected ? timestamp(NOW.minusSeconds(5)) : null)
                .update();
    }

    private String dispatchStatus(UUID dispatchId) {
        return value("SELECT status FROM dispatches WHERE id = :id", dispatchId);
    }

    private String commandStatus(UUID dispatchId) {
        return value("SELECT status FROM device_commands WHERE dispatch_id = :id", dispatchId);
    }

    private String commandReason(UUID dispatchId) {
        return value(
                "SELECT rejection_reason FROM device_commands WHERE dispatch_id = :id",
                dispatchId
        );
    }

    private String value(String sql, UUID id) {
        return jdbcClient.sql(sql).param("id", id).query(String.class).single();
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
