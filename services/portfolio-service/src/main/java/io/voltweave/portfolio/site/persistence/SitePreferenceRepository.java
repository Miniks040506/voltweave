package io.voltweave.portfolio.site.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.site.domain.entities.SitePreference;

@Repository
public class SitePreferenceRepository {
    private static final RowMapper<SitePreference> ROW_MAPPER = (resultSet, rowNumber) ->
            new SitePreference(
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getObject("site_id", UUID.class),
                    resultSet.getBoolean("vpp_opt_in"),
                    resultSet.getInt("minimum_battery_reserve_percent"),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public SitePreferenceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(SitePreference preference) {
        int rows = jdbcClient.sql("""
                INSERT INTO site_preferences (
                    site_id, organization_id, vpp_opt_in,
                    minimum_battery_reserve_percent, updated_at
                ) VALUES (
                    :siteId, :organizationId, :vppOptIn, :reservePercent, :updatedAt
                )
                """)
                .param("siteId", preference.siteId())
                .param("organizationId", preference.organizationId())
                .param("vppOptIn", preference.vppOptIn())
                .param("reservePercent", preference.minimumBatteryReservePercent())
                .param("updatedAt", Timestamp.from(preference.updatedAt()))
                .update();

        requireOneRow(rows, "inserted");
    }

    public void update(SitePreference preference) {
        int rows = jdbcClient.sql("""
                UPDATE site_preferences
                SET vpp_opt_in = :vppOptIn,
                    minimum_battery_reserve_percent = :reservePercent,
                    updated_at = :updatedAt
                WHERE site_id = :siteId AND organization_id = :organizationId
                """)
                .param("siteId", preference.siteId())
                .param("organizationId", preference.organizationId())
                .param("vppOptIn", preference.vppOptIn())
                .param("reservePercent", preference.minimumBatteryReservePercent())
                .param("updatedAt", Timestamp.from(preference.updatedAt()))
                .update();

        requireOneRow(rows, "updated");
    }

    public Optional<SitePreference> findBySiteIdForSubject(UUID siteId, String subjectId) {
        return jdbcClient.sql("""
                SELECT p.*
                FROM site_preferences p
                JOIN organization_members m
                  ON m.organization_id = p.organization_id
                WHERE p.site_id = :siteId
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
            throw new IllegalStateException(
                    "Expected one " + action + " site preference, got " + rows
            );
        }
    }
}
