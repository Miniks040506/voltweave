package io.voltweave.portfolio.messaging.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {
    private final JdbcClient jdbcClient;

    public OutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(
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
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }

    public List<OutboxEvent> lockReadyBatch(int limit) {
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
                UPDATE event_outbox
                SET published_at = :publishedAt, last_error = NULL
                WHERE event_id = :eventId AND published_at IS NULL
                """)
                .param("eventId", eventId)
                .param("publishedAt", Timestamp.from(publishedAt))
                .update();
    }

    public void markFailed(UUID eventId, Instant nextAttemptAt, String error) {
        jdbcClient.sql("""
                UPDATE event_outbox
                SET attempts = attempts + 1,
                    next_attempt_at = :nextAttemptAt,
                    last_error = :error
                WHERE event_id = :eventId AND published_at IS NULL
                """)
                .param("eventId", eventId)
                .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .param("error", error.substring(0, Math.min(error.length(), 1000)))
                .update();
    }
}
