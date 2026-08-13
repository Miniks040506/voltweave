package io.voltweave.dispatch.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DataIntegrityViolationException;

import io.voltweave.dispatch.application.model.DeviceCommand;
import io.voltweave.dispatch.domain.enums.CommandStatus;
import io.voltweave.dispatch.domain.enums.DispatchStatus;

@Repository
public class CommandRepository {
    public static final String ACK_CONSUMER = "dispatch-command-acks-v1";
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
                .query(this::mapCommand).list();
    }

    public void reserve(
            UUID organizationId,
            UUID dispatchId,
            List<UUID> deviceIds,
            Instant reservedFrom,
            Instant reservedUntil,
            Instant createdAt
    ) {
        try {
            for (UUID deviceId : deviceIds) {
                jdbcClient.sql("""
                        INSERT INTO device_reservations (
                            id, organization_id, dispatch_id, device_id,
                            reserved_from, reserved_until, created_at
                        ) VALUES (
                            :id, :organizationId, :dispatchId, :deviceId,
                            :reservedFrom, :reservedUntil, :createdAt
                        )
                        """)
                        .param("id", UUID.randomUUID())
                        .param("organizationId", organizationId)
                        .param("dispatchId", dispatchId)
                        .param("deviceId", deviceId)
                        .param("reservedFrom", timestamp(reservedFrom))
                        .param("reservedUntil", timestamp(reservedUntil))
                        .param("createdAt", timestamp(createdAt))
                        .update();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException(
                    "A device is already reserved by an overlapping dispatch", exception
            );
        }
    }

    public boolean recordAcknowledgementIfNew(
            UUID eventId,
            String eventType,
            Instant receivedAt
    ) {
        return jdbcClient.sql("""
                INSERT INTO event_inbox (consumer_name, event_id, event_type, received_at)
                VALUES (:consumer, :eventId, :eventType, :receivedAt)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)
                .param("consumer", ACK_CONSUMER)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("receivedAt", timestamp(receivedAt))
                .update() == 1;
    }

    public Optional<DeviceCommand> lockById(UUID organizationId, UUID commandId) {
        return jdbcClient.sql("""
                SELECT * FROM device_commands
                WHERE organization_id = :organizationId AND id = :commandId
                FOR UPDATE
                """)
                .param("organizationId", organizationId)
                .param("commandId", commandId)
                .query(this::mapCommand).optional();
    }

    public void acknowledge(
            UUID commandId,
            CommandStatus status,
            java.math.BigDecimal appliedPowerKw,
            String rejectionReason,
            Instant acknowledgedAt
    ) {
        jdbcClient.sql("""
                UPDATE device_commands
                SET status = :status, applied_power_kw = :appliedPowerKw,
                    rejection_reason = :rejectionReason,
                    acknowledged_at = :acknowledgedAt, version = version + 1
                WHERE id = :commandId AND status = 'REQUESTED'
                """)
                .param("commandId", commandId)
                .param("status", status.name())
                .param("appliedPowerKw", appliedPowerKw)
                .param("rejectionReason", rejectionReason)
                .param("acknowledgedAt", timestamp(acknowledgedAt))
                .update();
    }

    public void activateWhenAllCommandsAccepted(UUID organizationId, UUID dispatchId) {
        jdbcClient.sql("""
                UPDATE dispatches SET status = 'ACTIVE', version = version + 1
                WHERE organization_id = :organizationId AND id = :dispatchId
                  AND status IN ('PREPARING', 'REBALANCING')
                  AND EXISTS (
                    SELECT 1 FROM device_commands WHERE dispatch_id = :dispatchId
                  )
                  AND NOT EXISTS (
                    SELECT 1 FROM device_commands
                    WHERE dispatch_id = :dispatchId AND status <> 'ACCEPTED'
                  )
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .update();
    }

    public List<StalledCommand> lockStalledCommands(Instant now, int limit) {
        return jdbcClient.sql("""
                SELECT c.organization_id, c.dispatch_id, c.id
                FROM device_commands c
                JOIN dispatches d ON d.id = c.dispatch_id
                WHERE d.status IN ('PREPARING', 'REBALANCING')
                  AND (
                    c.status = 'REJECTED'
                    OR (c.status = 'REQUESTED' AND c.acknowledgement_deadline_at <= :now)
                  )
                ORDER BY c.acknowledgement_deadline_at, c.id
                LIMIT :limit FOR UPDATE OF c SKIP LOCKED
                """)
                .param("now", timestamp(now))
                .param("limit", limit)
                .query((row, rowNumber) -> new StalledCommand(
                        row.getObject("organization_id", UUID.class),
                        row.getObject("dispatch_id", UUID.class),
                        row.getObject("id", UUID.class)
                )).list();
    }

    public void timeOutUnacknowledged(UUID commandId, Instant now) {
        jdbcClient.sql("""
                UPDATE device_commands
                SET status = 'TIMED_OUT', rejection_reason = 'ACKNOWLEDGEMENT_TIMEOUT',
                    acknowledged_at = :now, version = version + 1
                WHERE id = :commandId AND status = 'REQUESTED'
                  AND acknowledgement_deadline_at <= :now
                """)
                .param("commandId", commandId)
                .param("now", timestamp(now))
                .update();
    }

    public void failPreparingDispatch(UUID organizationId, UUID dispatchId) {
        jdbcClient.sql("""
                UPDATE dispatches SET status = 'FAILED', version = version + 1
                WHERE organization_id = :organizationId AND id = :dispatchId
                  AND status IN ('PREPARING', 'REBALANCING')
                  AND EXISTS (
                    SELECT 1 FROM device_commands
                    WHERE dispatch_id = :dispatchId
                      AND status IN ('REJECTED', 'TIMED_OUT')
                  )
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .update();
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
                    acknowledgement_deadline_at, status, requested_at, version
                ) VALUES (
                    :id, :organizationId, :dispatchId, :siteId, :deviceId,
                    :commandType, :targetPowerKw, :validFrom, :expiresAt,
                    :acknowledgementDeadlineAt, :status, :requestedAt, :version
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
                .param("acknowledgementDeadlineAt", timestamp(command.acknowledgementDeadlineAt()))
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

    public record StalledCommand(UUID organizationId, UUID dispatchId, UUID commandId) {
    }

    private DeviceCommand mapCommand(java.sql.ResultSet row, int rowNumber)
            throws java.sql.SQLException {
        return new DeviceCommand(
                row.getObject("id", UUID.class), row.getObject("organization_id", UUID.class),
                row.getObject("dispatch_id", UUID.class), row.getObject("site_id", UUID.class),
                row.getObject("device_id", UUID.class), row.getString("command_type"),
                row.getBigDecimal("target_power_kw"), row.getTimestamp("valid_from").toInstant(),
                row.getTimestamp("acknowledgement_deadline_at").toInstant(),
                row.getTimestamp("expires_at").toInstant(),
                CommandStatus.valueOf(row.getString("status")),
                row.getBigDecimal("applied_power_kw"), row.getString("rejection_reason"),
                row.getTimestamp("requested_at").toInstant(),
                row.getTimestamp("acknowledged_at") == null ? null
                        : row.getTimestamp("acknowledged_at").toInstant(),
                row.getLong("version")
        );
    }
}
