package io.voltweave.portfolio.organization.repository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.organization.domain.Organization;
import io.voltweave.portfolio.organization.domain.OrganizationStatus;
import io.voltweave.portfolio.organization.domain.OrganizationType;

@Repository
public class OrganizationRepository {
    private static final RowMapper<Organization> ROW_MAPPER = (resultSet, rowNumber) ->
            new Organization(
                    resultSet.getObject("id", UUID.class),
                    OrganizationType.valueOf(resultSet.getString("type")),
                    resultSet.getString("legal_name"),
                    resultSet.getString("display_name"),
                    resultSet.getString("tenant_code"),
                    OrganizationStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("country"),
                    resultSet.getString("timezone"),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public OrganizationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(Organization organization) {
        int rows = jdbcClient.sql("""
                INSERT INTO organizations (
                    id, type, legal_name, display_name, tenant_code,
                    status, country, timezone, created_at, updated_at
                ) VALUES (
                    :id, :type, :legalName, :displayName, :tenantCode,
                    :status, :country, :timezone, :createdAt, :updatedAt
                )
                """)
                .param("id", organization.id())
                .param("type", organization.type().name())
                .param("legalName", organization.legalName())
                .param("displayName", organization.displayName())
                .param("tenantCode", organization.tenantCode())
                .param("status", organization.status().name())
                .param("country", organization.country())
                .param("timezone", organization.timezone())
                .param("createdAt", Timestamp.from(organization.createdAt()))
                .param("updatedAt", Timestamp.from(organization.updatedAt()))
                .update();

        if (rows != 1) {
            throw new IllegalStateException("Expected one inserted organization, got " + rows);
        }
    }

    public Optional<Organization> findByIdForSubject(UUID organizationId, String subjectId) {
        return jdbcClient.sql("""
                SELECT o.*
                FROM organizations o
                JOIN organization_members m ON m.organization_id = o.id
                WHERE o.id = :organizationId
                  AND m.subject_id = :subjectId
                  AND m.status = 'ACTIVE'
                """)
                .param("organizationId", organizationId)
                .param("subjectId", subjectId)
                .query(ROW_MAPPER)
                .optional();
    }
}
