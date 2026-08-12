package io.voltweave.telemetry.processing.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TelemetryProcessingRepository {
    private final JdbcClient jdbcClient;

    public TelemetryProcessingRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean recordEventIfNew(
            String consumerName,
            UUID eventId,
            String eventType,
            Instant receivedAt
    ) {
        return jdbcClient.sql("""
                INSERT INTO event_inbox (consumer_name, event_id, event_type, received_at)
                VALUES (:consumerName, :eventId, :eventType, :receivedAt)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)
                .param("consumerName", consumerName)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("receivedAt", timestamp(receivedAt))
                .update() == 1;
    }

    public boolean claimSequence(
            UUID organizationId,
            UUID deviceId,
            long sequenceNumber,
            Instant observedAt,
            Instant expiresAt
    ) {
        return jdbcClient.sql("""
                INSERT INTO telemetry_dedup (
                    organization_id, device_id, sequence_number, observed_at, expires_at
                ) VALUES (
                    :organizationId, :deviceId, :sequenceNumber, :observedAt, :expiresAt
                )
                ON CONFLICT (device_id, sequence_number) DO NOTHING
                """)
                .param("organizationId", organizationId)
                .param("deviceId", deviceId)
                .param("sequenceNumber", sequenceNumber)
                .param("observedAt", timestamp(observedAt))
                .param("expiresAt", timestamp(expiresAt))
                .update() == 1;
    }

    public Optional<Instant> latestObservedAt(UUID organizationId, UUID deviceId) {
        return jdbcClient.sql("""
                SELECT max(observed_at) AS latest
                FROM telemetry_dedup
                WHERE organization_id = :organizationId AND device_id = :deviceId
                """)
                .param("organizationId", organizationId)
                .param("deviceId", deviceId)
                .query((resultSet, rowNumber) -> {
                    Timestamp value = resultSet.getTimestamp("latest");
                    return value == null ? null : value.toInstant();
                })
                .optional();
    }

    public void quarantine(
            UUID id,
            UUID organizationId,
            UUID deviceId,
            String reasonCode,
            String reasonDetail,
            String rawPayload,
            Instant receivedAt
    ) {
        jdbcClient.sql("""
                INSERT INTO quarantined_telemetry (
                    id, organization_id, device_id, reason_code,
                    reason_detail, raw_payload, received_at
                ) VALUES (
                    :id, :organizationId, :deviceId, :reasonCode,
                    :reasonDetail, CAST(:rawPayload AS JSONB), :receivedAt
                )
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", id)
                .param("organizationId", organizationId)
                .param("deviceId", deviceId)
                .param("reasonCode", reasonCode)
                .param("reasonDetail", truncate(reasonDetail, 500))
                .param("rawPayload", rawPayload)
                .param("receivedAt", timestamp(receivedAt))
                .update();
    }

    public void enqueue(
            UUID eventId,
            String topic,
            String partitionKey,
            String payload,
            Instant occurredAt
    ) {
        jdbcClient.sql("""
                INSERT INTO event_outbox (
                    event_id, topic, partition_key, payload, occurred_at
                ) VALUES (
                    :eventId, :topic, :partitionKey, CAST(:payload AS JSONB), :occurredAt
                )
                """)
                .param("eventId", eventId)
                .param("topic", topic)
                .param("partitionKey", partitionKey)
                .param("payload", payload)
                .param("occurredAt", timestamp(occurredAt))
                .update();
    }

    public List<OutboxEvent> lockReadyOutbox(int limit) {
        return jdbcClient.sql("""
                SELECT event_id, topic, partition_key, payload::text, occurred_at, attempts
                FROM event_outbox
                WHERE published_at IS NULL AND next_attempt_at <= now()
                ORDER BY occurred_at, event_id
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
                """)
                .param("limit", limit)
                .query((resultSet, rowNumber) -> new OutboxEvent(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getString("topic"),
                        resultSet.getString("partition_key"),
                        resultSet.getString("payload"),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getInt("attempts")
                ))
                .list();
    }

    public void markPublished(UUID eventId, Instant publishedAt) {
        jdbcClient.sql("""
                UPDATE event_outbox SET published_at = :publishedAt, last_error = NULL
                WHERE event_id = :eventId AND published_at IS NULL
                """)
                .param("eventId", eventId)
                .param("publishedAt", timestamp(publishedAt))
                .update();
    }

    public void markFailed(UUID eventId, Instant nextAttemptAt, String error) {
        jdbcClient.sql("""
                UPDATE event_outbox
                SET attempts = attempts + 1, next_attempt_at = :nextAttemptAt,
                    last_error = :error
                WHERE event_id = :eventId AND published_at IS NULL
                """)
                .param("eventId", eventId)
                .param("nextAttemptAt", timestamp(nextAttemptAt))
                .param("error", truncate(error, 1000))
                .update();
    }

    public int deleteExpiredDedup(Instant now, int limit) {
        return jdbcClient.sql("""
                DELETE FROM telemetry_dedup
                WHERE ctid IN (
                    SELECT ctid FROM telemetry_dedup
                    WHERE expires_at <= :now ORDER BY expires_at LIMIT :limit
                )
                """)
                .param("now", timestamp(now))
                .param("limit", limit)
                .update();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static String truncate(String value, int maxLength) {
        String safe = value == null || value.isBlank() ? "Unknown processing error" : value;
        return safe.substring(0, Math.min(safe.length(), maxLength));
    }
}
