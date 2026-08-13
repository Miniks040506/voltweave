package io.voltweave.telemetry.command.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.contracts.events.command.v1.CommandRequestedPayloadV1;
import io.voltweave.telemetry.command.application.model.CommandDelivery;

@Repository
public class CommandGatewayRepository {
    private static final String CONSUMER = "telemetry-command-gateway-v1";

    private final JdbcClient jdbcClient;

    public CommandGatewayRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean recordEventIfNew(UUID eventId, String eventType, Instant receivedAt) {
        return jdbcClient.sql("""
                INSERT INTO event_inbox (consumer_name, event_id, event_type, received_at)
                VALUES (:consumer, :eventId, :eventType, :receivedAt)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)
                .param("consumer", CONSUMER)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("receivedAt", timestamp(receivedAt))
                .update() == 1;
    }

    public void insert(
            UUID organizationId,
            UUID correlationId,
            UUID requestedEventId,
            CommandRequestedPayloadV1 command,
            String mqttPayload,
            Instant receivedAt
    ) {
        String topic = "voltweave/%s/%s/%s/command".formatted(
                organizationId, command.siteId(), command.deviceId()
        );
        jdbcClient.sql("""
                INSERT INTO command_deliveries (
                    command_id, organization_id, dispatch_id, site_id, device_id,
                    correlation_id, requested_event_id, mqtt_topic, mqtt_payload,
                    valid_from, acknowledgement_deadline_at, expires_at, received_at
                ) VALUES (
                    :commandId, :organizationId, :dispatchId, :siteId, :deviceId,
                    :correlationId, :requestedEventId, :mqttTopic, CAST(:mqttPayload AS JSONB),
                    :validFrom, :acknowledgementDeadlineAt, :expiresAt, :receivedAt
                ) ON CONFLICT (command_id) DO NOTHING
                """)
                .param("commandId", command.commandId())
                .param("organizationId", organizationId)
                .param("dispatchId", command.dispatchId())
                .param("siteId", command.siteId())
                .param("deviceId", command.deviceId())
                .param("correlationId", correlationId)
                .param("requestedEventId", requestedEventId)
                .param("mqttTopic", topic)
                .param("mqttPayload", mqttPayload)
                .param("validFrom", timestamp(command.validFrom()))
                .param("acknowledgementDeadlineAt", timestamp(command.acknowledgementDeadlineAt()))
                .param("expiresAt", timestamp(command.expiresAt()))
                .param("receivedAt", timestamp(receivedAt))
                .update();
    }

    public List<CommandDelivery> lockReady(Instant now, int limit) {
        return jdbcClient.sql("""
                SELECT command_id, organization_id, dispatch_id, site_id, device_id,
                       mqtt_topic, mqtt_payload::text, valid_from, expires_at, attempts
                       , acknowledgement_deadline_at
                FROM command_deliveries
                WHERE status IN ('PENDING', 'PUBLISHED')
                  AND valid_from <= :now AND acknowledgement_deadline_at > :now
                  AND next_attempt_at <= :now
                ORDER BY valid_from, command_id
                LIMIT :limit FOR UPDATE SKIP LOCKED
                """)
                .param("now", timestamp(now))
                .param("limit", limit)
                .query((row, rowNumber) -> new CommandDelivery(
                        row.getObject("command_id", UUID.class),
                        row.getObject("organization_id", UUID.class),
                        row.getObject("dispatch_id", UUID.class),
                        row.getObject("site_id", UUID.class),
                        row.getObject("device_id", UUID.class),
                        row.getString("mqtt_topic"), row.getString("mqtt_payload"),
                        row.getTimestamp("valid_from").toInstant(),
                        row.getTimestamp("acknowledgement_deadline_at").toInstant(),
                        row.getTimestamp("expires_at").toInstant(), row.getInt("attempts")
                )).list();
    }

    public void markPublished(UUID commandId, Instant publishedAt, Instant nextAttemptAt) {
        jdbcClient.sql("""
                UPDATE command_deliveries
                SET status = 'PUBLISHED', published_at = COALESCE(published_at, :publishedAt),
                    attempts = attempts + 1, next_attempt_at = :nextAttemptAt,
                    last_error = NULL
                WHERE command_id = :commandId AND status IN ('PENDING', 'PUBLISHED')
                """)
                .param("commandId", commandId)
                .param("publishedAt", timestamp(publishedAt))
                .param("nextAttemptAt", timestamp(nextAttemptAt))
                .update();
    }

    public void markFailed(UUID commandId, Instant nextAttemptAt, String error) {
        String safe = error == null || error.isBlank() ? "Unknown MQTT error" : error;
        jdbcClient.sql("""
                UPDATE command_deliveries
                SET attempts = attempts + 1, next_attempt_at = :nextAttemptAt,
                    last_error = :error
                WHERE command_id = :commandId AND status IN ('PENDING', 'PUBLISHED')
                """)
                .param("commandId", commandId)
                .param("nextAttemptAt", timestamp(nextAttemptAt))
                .param("error", safe.substring(0, Math.min(safe.length(), 1000)))
                .update();
    }

    public Optional<CommandIdentity> findIdentity(UUID commandId) {
        return jdbcClient.sql("""
                SELECT organization_id, dispatch_id, site_id, device_id,
                       correlation_id, requested_event_id
                FROM command_deliveries WHERE command_id = :commandId
                """)
                .param("commandId", commandId)
                .query((row, rowNumber) -> new CommandIdentity(
                        row.getObject("organization_id", UUID.class),
                        row.getObject("dispatch_id", UUID.class),
                        row.getObject("site_id", UUID.class),
                        row.getObject("device_id", UUID.class),
                        row.getObject("correlation_id", UUID.class),
                        row.getObject("requested_event_id", UUID.class)
                )).optional();
    }

    public boolean acknowledge(UUID commandId, Instant acknowledgedAt) {
        return jdbcClient.sql("""
                UPDATE command_deliveries
                SET status = 'ACKNOWLEDGED', acknowledged_at = :acknowledgedAt,
                    published_at = COALESCE(published_at, :acknowledgedAt), last_error = NULL
                WHERE command_id = :commandId AND status <> 'ACKNOWLEDGED'
                """)
                .param("commandId", commandId)
                .param("acknowledgedAt", timestamp(acknowledgedAt))
                .update() == 1;
    }

    public record CommandIdentity(
            UUID organizationId,
            UUID dispatchId,
            UUID siteId,
            UUID deviceId,
            UUID correlationId,
            UUID requestedEventId
    ) {
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
