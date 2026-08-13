package io.voltweave.dispatch.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.dispatch.domain.enums.DispatchStatus;

@Repository
public class CompletionRepository {
    private final JdbcClient jdbcClient;

    public CompletionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<UUID> dueDispatchIds(Instant now, int limit) {
        return jdbcClient.sql("""
                SELECT id FROM dispatches
                WHERE status IN ('ACTIVE', 'REBALANCING')
                  AND scheduled_end_at <= :now
                ORDER BY scheduled_end_at, id LIMIT :limit
                """)
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .query(UUID.class).list();
    }

    public boolean lock(UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT id FROM dispatches WHERE id = :dispatchId FOR UPDATE
                """)
                .param("dispatchId", dispatchId)
                .query(UUID.class).optional().isPresent();
    }

    public BigDecimal deliveredEnergy(UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT COALESCE(sum(cumulative_delivered_energy_kwh), 0)
                FROM (
                    SELECT DISTINCT ON (device_id)
                           device_id, cumulative_delivered_energy_kwh
                    FROM dispatch_performance_points
                    WHERE dispatch_id = :dispatchId
                    ORDER BY device_id, observed_at DESC
                ) latest
                """)
                .param("dispatchId", dispatchId)
                .query(BigDecimal.class).single();
    }

    public void transition(
            UUID dispatchId,
            DispatchStatus expected,
            DispatchStatus target
    ) {
        int updated = jdbcClient.sql("""
                UPDATE dispatches SET status = :target, version = version + 1
                WHERE id = :dispatchId AND status = :expected
                """)
                .param("dispatchId", dispatchId)
                .param("expected", expected.name())
                .param("target", target.name())
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Dispatch changed during completion");
        }
    }

    public void insertOutbox(
            UUID eventId,
            UUID dispatchId,
            String topic,
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
                .param("partitionKey", dispatchId.toString())
                .param("payload", payload)
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }
}
