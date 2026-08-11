package io.voltweave.portfolio.organization;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OrganizationMemberRepository {
    private static final RowMapper<OrganizationMember> ROW_MAPPER = (resultSet, rowNumber) ->
            new OrganizationMember(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getString("subject_id"),
                    OrganizationRole.valueOf(resultSet.getString("role")),
                    MembershipStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("created_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public OrganizationMemberRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(OrganizationMember member) {
        int rows = jdbcClient.sql("""
                INSERT INTO organization_members (
                    id, organization_id, subject_id, role, status, created_at
                ) VALUES (
                    :id, :organizationId, :subjectId, :role, :status, :createdAt
                )
                """)
                .param("id", member.id())
                .param("organizationId", member.organizationId())
                .param("subjectId", member.subjectId())
                .param("role", member.role().name())
                .param("status", member.status().name())
                .param("createdAt", Timestamp.from(member.createdAt()))
                .update();

        if (rows != 1) {
            throw new IllegalStateException("Expected one inserted member, got " + rows);
        }
    }

    public Optional<OrganizationMember> findActive(
            UUID organizationId,
            String subjectId
    ) {
        return jdbcClient.sql("""
                SELECT *
                FROM organization_members
                WHERE organization_id = :organizationId
                  AND subject_id = :subjectId
                  AND status = 'ACTIVE'
                """)
                .param("organizationId", organizationId)
                .param("subjectId", subjectId)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<OrganizationMember> findActiveBySubject(String subjectId) {
        return jdbcClient.sql("""
                SELECT *
                FROM organization_members
                WHERE subject_id = :subjectId
                  AND status = 'ACTIVE'
                ORDER BY organization_id
                """)
                .param("subjectId", subjectId)
                .query(ROW_MAPPER)
                .list();
    }
}
