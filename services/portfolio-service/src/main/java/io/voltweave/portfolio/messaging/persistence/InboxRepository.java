package io.voltweave.portfolio.messaging.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InboxRepository {
    private final JdbcClient jdbcClient;

    public InboxRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean recordIfNew(
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
                .param("receivedAt", Timestamp.from(receivedAt))
                .update() == 1;
    }
}
