package io.voltweave.portfolio.vpp.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.vpp.domain.entities.VppMembership;
import io.voltweave.portfolio.vpp.domain.enums.VppMembershipStatus;

@Repository
public class VppMembershipRepository {
    private static final RowMapper<VppMembership> ROW_MAPPER =
            (resultSet, rowNumber) -> new VppMembership(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("vpp_organization_id", UUID.class),
                    resultSet.getObject("site_organization_id", UUID.class),
                    resultSet.getObject("vpp_id", UUID.class),
                    resultSet.getObject("site_id", UUID.class),
                    VppMembershipStatus.valueOf(resultSet.getString("status")),
                    resultSet.getBigDecimal("participation_weight"),
                    resultSet.getTimestamp("joined_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public VppMembershipRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(VppMembership membership) {
        int rows = jdbcClient.sql("""
                INSERT INTO vpp_memberships (
                    id, vpp_organization_id, site_organization_id, vpp_id, site_id,
                    status, participation_weight, joined_at, updated_at
                ) VALUES (
                    :id, :vppOrganizationId, :siteOrganizationId, :vppId, :siteId,
                    :status, :participationWeight, :joinedAt, :updatedAt
                )
                """)
                .param("id", membership.id())
                .param("vppOrganizationId", membership.vppOrganizationId())
                .param("siteOrganizationId", membership.siteOrganizationId())
                .param("vppId", membership.vppId())
                .param("siteId", membership.siteId())
                .param("status", membership.status().name())
                .param("participationWeight", membership.participationWeight())
                .param("joinedAt", Timestamp.from(membership.joinedAt()))
                .param("updatedAt", Timestamp.from(membership.updatedAt()))
                .update();
        requireOneRow(rows, "inserted");
    }

    public void update(VppMembership membership) {
        int rows = jdbcClient.sql("""
                UPDATE vpp_memberships
                SET status = :status,
                    participation_weight = :participationWeight,
                    joined_at = :joinedAt,
                    updated_at = :updatedAt
                WHERE id = :id
                  AND vpp_organization_id = :vppOrganizationId
                  AND vpp_id = :vppId
                """)
                .param("id", membership.id())
                .param("vppOrganizationId", membership.vppOrganizationId())
                .param("vppId", membership.vppId())
                .param("status", membership.status().name())
                .param("participationWeight", membership.participationWeight())
                .param("joinedAt", Timestamp.from(membership.joinedAt()))
                .param("updatedAt", Timestamp.from(membership.updatedAt()))
                .update();
        requireOneRow(rows, "updated");
    }

    public Optional<VppMembership> find(UUID organizationId, UUID vppId, UUID siteId) {
        return jdbcClient.sql("""
                SELECT * FROM vpp_memberships
                WHERE vpp_organization_id = :organizationId
                  AND vpp_id = :vppId
                  AND site_id = :siteId
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("siteId", siteId)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<VppMembership> findActiveByVpp(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT * FROM vpp_memberships
                WHERE vpp_organization_id = :organizationId
                  AND vpp_id = :vppId
                  AND status = 'ACTIVE'
                ORDER BY joined_at, site_id
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query(ROW_MAPPER)
                .list();
    }

    private static void requireOneRow(int rows, String action) {
        if (rows != 1) {
            throw new IllegalStateException(
                    "Expected one " + action + " VPP membership, got " + rows
            );
        }
    }
}
