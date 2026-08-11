package io.voltweave.portfolio.site.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.site.domain.entity.Site;
import io.voltweave.portfolio.site.domain.enums.SiteStatus;

@Repository
public class SiteRepository {
    private static final RowMapper<Site> ROW_MAPPER = (resultSet, rowNumber) ->
            new Site(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getString("name"),
                    resultSet.getString("timezone"),
                    resultSet.getString("region"),
                    resultSet.getString("country"),
                    SiteStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public SiteRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(Site site) {
        int rows = jdbcClient.sql("""
                INSERT INTO sites (
                    id, organization_id, name, timezone, region, country,
                    status, created_at, updated_at
                ) VALUES (
                    :id, :organizationId, :name, :timezone, :region, :country,
                    :status, :createdAt, :updatedAt
                )
                """)
                .param("id", site.id())
                .param("organizationId", site.organizationId())
                .param("name", site.name())
                .param("timezone", site.timezone())
                .param("region", site.region())
                .param("country", site.country())
                .param("status", site.status().name())
                .param("createdAt", Timestamp.from(site.createdAt()))
                .param("updatedAt", Timestamp.from(site.updatedAt()))
                .update();

        requireOneRow(rows, "inserted");
    }

    public void update(Site site) {
        int rows = jdbcClient.sql("""
                UPDATE sites
                SET name = :name,
                    timezone = :timezone,
                    region = :region,
                    country = :country,
                    updated_at = :updatedAt
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", site.id())
                .param("organizationId", site.organizationId())
                .param("name", site.name())
                .param("timezone", site.timezone())
                .param("region", site.region())
                .param("country", site.country())
                .param("updatedAt", Timestamp.from(site.updatedAt()))
                .update();

        requireOneRow(rows, "updated");
    }

    public Optional<Site> findByIdForSubject(UUID siteId, String subjectId) {
        return jdbcClient.sql("""
                SELECT s.*
                FROM sites s
                JOIN organization_members m
                  ON m.organization_id = s.organization_id
                WHERE s.id = :siteId
                  AND m.subject_id = :subjectId
                  AND m.status = 'ACTIVE'
                """)
                .param("siteId", siteId)
                .param("subjectId", subjectId)
                .query(ROW_MAPPER)
                .optional();
    }

    private static void requireOneRow(int rows, String action) {
        if (rows != 1) {
            throw new IllegalStateException("Expected one " + action + " site, got " + rows);
        }
    }
}
