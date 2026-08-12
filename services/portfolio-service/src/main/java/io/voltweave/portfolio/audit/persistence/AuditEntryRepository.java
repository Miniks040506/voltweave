package io.voltweave.portfolio.audit.persistence;

import java.sql.Timestamp;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.audit.domain.entities.AuditEntry;

@Repository
public class AuditEntryRepository {
    private final JdbcClient jdbcClient;

    public AuditEntryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(AuditEntry entry) {
        int rows = jdbcClient.sql("""
                INSERT INTO audit_entries (
                    id, organization_id, actor_type, actor_id, action,
                    resource_type, resource_id, occurred_at, correlation_id
                ) VALUES (
                    :id, :organizationId, :actorType, :actorId, :action,
                    :resourceType, :resourceId, :occurredAt, :correlationId
                )
                """)
                .param("id", entry.id())
                .param("organizationId", entry.organizationId())
                .param("actorType", entry.actorType().name())
                .param("actorId", entry.actorId())
                .param("action", entry.action().name())
                .param("resourceType", entry.resourceType().name())
                .param("resourceId", entry.resourceId())
                .param("occurredAt", Timestamp.from(entry.occurredAt()))
                .param("correlationId", entry.correlationId())
                .update();
        if (rows != 1) {
            throw new IllegalStateException("Expected one inserted audit entry, got " + rows);
        }
    }
}
