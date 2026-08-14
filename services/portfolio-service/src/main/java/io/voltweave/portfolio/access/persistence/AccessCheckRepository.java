package io.voltweave.portfolio.access.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.access.application.model.AccessCheckResult;
import io.voltweave.portfolio.access.domain.enums.AccessResourceType;
import io.voltweave.portfolio.organization.domain.enums.OrganizationRole;

@Repository
public class AccessCheckRepository {
    private final JdbcClient jdbcClient;

    public AccessCheckRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<AccessCheckResult> findGrant(
            String subjectId,
            AccessResourceType resourceType,
            UUID resourceId
    ) {
        String resourceSql = switch (resourceType) {
            case ORGANIZATION -> "SELECT id, id AS organization_id FROM organizations";
            case SITE -> "SELECT id, organization_id FROM sites";
            case DEVICE -> "SELECT id, organization_id FROM devices";
            case VPP -> "SELECT id, organization_id FROM virtual_power_plants";
        };

        return jdbcClient.sql("""
                SELECT resource.organization_id, member.role
                FROM (%s) resource
                JOIN organization_members member
                  ON member.organization_id = resource.organization_id
                 AND member.subject_id = :subjectId
                 AND member.status = 'ACTIVE'
                WHERE resource.id = :resourceId
                """.formatted(resourceSql))
                .param("subjectId", subjectId)
                .param("resourceId", resourceId)
                .query((resultSet, rowNumber) -> new AccessCheckResult(
                        true,
                        resultSet.getObject("organization_id", UUID.class),
                        OrganizationRole.valueOf(resultSet.getString("role"))
                ))
                .optional();
    }

    public List<UUID> findSiteIdsForSubject(String subjectId) {
        return jdbcClient.sql("""
                SELECT site.id
                FROM sites site
                JOIN organization_members member
                  ON member.organization_id = site.organization_id
                 AND member.subject_id = :subjectId
                 AND member.status = 'ACTIVE'
                ORDER BY site.id
                """)
                .param("subjectId", subjectId)
                .query(UUID.class).list();
    }
}
