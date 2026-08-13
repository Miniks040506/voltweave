package io.voltweave.dispatch.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.dispatch.application.model.DeviceCommand;
import io.voltweave.dispatch.domain.enums.CommandStatus;
import io.voltweave.dispatch.domain.enums.DispatchStatus;

@Repository
public class CommandRepository {
    private final JdbcClient jdbcClient;

    public CommandRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void lockDispatch(UUID dispatchId) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .param("scope", "dispatch-commands:" + dispatchId)
                .query((row, rowNumber) -> 0).single();
    }

    public List<DeviceCommand> findByDispatch(UUID organizationId, UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT * FROM device_commands
                WHERE organization_id = :organizationId AND dispatch_id = :dispatchId
                ORDER BY device_id
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .query((row, rowNumber) -> new DeviceCommand(
                        row.getObject("id", UUID.class),
                        row.getObject("organization_id", UUID.class),
                        row.getObject("dispatch_id", UUID.class),
                        row.getObject("site_id", UUID.class),
                        row.getObject("device_id", UUID.class),
                        row.getString("command_type"), row.getBigDecimal("target_power_kw"),
                        row.getTimestamp("valid_from").toInstant(),
                        row.getTimestamp("expires_at").toInstant(),
                        CommandStatus.valueOf(row.getString("status")),
                        row.getBigDecimal("applied_power_kw"),
                        row.getString("rejection_reason"),
                        row.getTimestamp("requested_at").toInstant(),
                        row.getTimestamp("acknowledged_at") == null ? null
                                : row.getTimestamp("acknowledged_at").toInstant(),
                        row.getLong("version")
                )).list();
    }

    public void insert(
            DeviceCommand command,
            UUID eventId,
            String eventTopic,
            String eventPayload
    ) {
        jdbcClient.sql("""
                INSERT INTO device_commands (
                    id, organization_id, dispatch_id, site_id, device_id,
                    command_type, target_power_kw, valid_from, expires_at,
                    status, requested_at, version
                ) VALUES (
                    :id, :organizationId, :dispatchId, :siteId, :deviceId,
                    :commandType, :targetPowerKw, :validFrom, :expiresAt,
                    :status, :requestedAt, :version
                )
                """)
                .param("id", command.id())
                .param("organizationId", command.organizationId())
                .param("dispatchId", command.dispatchId())
                .param("siteId", command.siteId())
                .param("deviceId", command.deviceId())
                .param("commandType", command.commandType())
                .param("targetPowerKw", command.targetPowerKw())
                .param("validFrom", timestamp(command.validFrom()))
                .param("expiresAt", timestamp(command.expiresAt()))
                .param("status", command.status().name())
                .param("requestedAt", timestamp(command.requestedAt()))
                .param("version", command.version())
                .update();
        jdbcClient.sql("""
                INSERT INTO event_outbox (
                    event_id, topic, partition_key, payload, occurred_at
                ) VALUES (
                    :eventId, :topic, :partitionKey,
                    CAST(:payload AS JSONB), :occurredAt
                )
                """)
                .param("eventId", eventId)
                .param("topic", eventTopic)
                .param("partitionKey", command.deviceId().toString())
                .param("payload", eventPayload)
                .param("occurredAt", timestamp(command.requestedAt()))
                .update();
    }

    public void transitionDispatch(
            UUID organizationId,
            UUID dispatchId,
            DispatchStatus expected,
            DispatchStatus target,
            long expectedVersion
    ) {
        int updated = jdbcClient.sql("""
                UPDATE dispatches SET status = :target, version = version + 1
                WHERE organization_id = :organizationId AND id = :dispatchId
                  AND status = :expected AND version = :expectedVersion
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .param("expected", expected.name())
                .param("target", target.name())
                .param("expectedVersion", expectedVersion)
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Dispatch changed while commands were prepared");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
