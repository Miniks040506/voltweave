package io.voltweave.portfolio.audit.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.audit.domain.entities.AuditEntry;
import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditActorType;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;

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

    public List<AuditEntry> findForSubject(
            UUID organizationId,
            String subjectId,
            int limit
    ) {
        return jdbcClient.sql("""
                SELECT audit.*
                FROM audit_entries audit
                JOIN organization_members member
                  ON member.organization_id = audit.organization_id
                 AND member.subject_id = :subjectId
                 AND member.status = 'ACTIVE'
                WHERE audit.organization_id = :organizationId
                ORDER BY audit.occurred_at DESC, audit.id DESC
                LIMIT :limit
                """)
                .param("organizationId", organizationId)
                .param("subjectId", subjectId)
                .param("limit", limit)
                .query((resultSet, rowNumber) -> new AuditEntry(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("organization_id", UUID.class),
                        AuditActorType.valueOf(resultSet.getString("actor_type")),
                        resultSet.getString("actor_id"),
                        AuditAction.valueOf(resultSet.getString("action")),
                        AuditResourceType.valueOf(resultSet.getString("resource_type")),
                        resultSet.getObject("resource_id", UUID.class),
                        resultSet.getTimestamp("occurred_at").toInstant(),
                        resultSet.getObject("correlation_id", UUID.class),
                        resultSet.getString("ip_address"),
                        resultSet.getString("user_agent")
                ))
                .list();
    }
}
