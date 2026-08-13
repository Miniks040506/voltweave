package io.voltweave.dispatch.messaging;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CommandOutboxRepository {
    private final JdbcClient jdbcClient;

    CommandOutboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    List<OutboxEvent> lockReady(int limit) {
        return jdbcClient.sql("""
                SELECT event_id, topic, partition_key, payload::text, occurred_at, attempts
                FROM event_outbox
                WHERE published_at IS NULL AND next_attempt_at <= now()
                ORDER BY occurred_at, event_id
                LIMIT :limit FOR UPDATE SKIP LOCKED
                """)
                .param("limit", limit)
                .query((row, rowNumber) -> new OutboxEvent(
                        row.getObject("event_id", UUID.class), row.getString("topic"),
                        row.getString("partition_key"), row.getString("payload"),
                        row.getTimestamp("occurred_at").toInstant(), row.getInt("attempts")
                )).list();
    }

    void markPublished(UUID eventId, Instant publishedAt) {
        jdbcClient.sql("""
                UPDATE event_outbox SET published_at = :publishedAt, last_error = NULL
                WHERE event_id = :eventId AND published_at IS NULL
                """)
                .param("eventId", eventId)
                .param("publishedAt", Timestamp.from(publishedAt))
                .update();
    }

    void markFailed(UUID eventId, Instant nextAttemptAt, String error) {
        String safe = error == null || error.isBlank() ? "Unknown Kafka error" : error;
        jdbcClient.sql("""
                UPDATE event_outbox
                SET attempts = attempts + 1, next_attempt_at = :nextAttemptAt,
                    last_error = :error
                WHERE event_id = :eventId AND published_at IS NULL
                """)
                .param("eventId", eventId)
                .param("nextAttemptAt", Timestamp.from(nextAttemptAt))
                .param("error", safe.substring(0, Math.min(safe.length(), 1000)))
                .update();
    }
}
