package io.voltweave.portfolio.device.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ApiIdempotencyRepository {
    private final JdbcClient jdbcClient;

    public ApiIdempotencyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean insert(Entry entry) {
        return jdbcClient.sql("""
                INSERT INTO api_idempotency_records (
                    organization_id, operation, idempotency_key,
                    request_hash, resource_id, created_at
                ) VALUES (
                    :organizationId, :operation, :idempotencyKey,
                    :requestHash, :resourceId, :createdAt
                )
                ON CONFLICT DO NOTHING
                """)
                .param("organizationId", entry.organizationId())
                .param("operation", entry.operation())
                .param("idempotencyKey", entry.idempotencyKey())
                .param("requestHash", entry.requestHash())
                .param("resourceId", entry.resourceId())
                .param("createdAt", Timestamp.from(entry.createdAt()))
                .update() == 1;
    }

    public Optional<Entry> find(UUID organizationId, String operation, String key) {
        return jdbcClient.sql("""
                SELECT * FROM api_idempotency_records
                WHERE organization_id = :organizationId
                  AND operation = :operation
                  AND idempotency_key = :idempotencyKey
                """)
                .param("organizationId", organizationId)
                .param("operation", operation)
                .param("idempotencyKey", key)
                .query((resultSet, rowNumber) -> new Entry(
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getString("operation"),
                        resultSet.getString("idempotency_key"),
                        resultSet.getString("request_hash"),
                        resultSet.getObject("resource_id", UUID.class),
                        resultSet.getTimestamp("created_at").toInstant()
                ))
                .optional();
    }

    public record Entry(
            UUID organizationId,
            String operation,
            String idempotencyKey,
            String requestHash,
            UUID resourceId,
            Instant createdAt
    ) {
    }
}
