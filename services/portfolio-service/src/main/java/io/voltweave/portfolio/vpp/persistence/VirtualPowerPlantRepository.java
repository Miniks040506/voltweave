package io.voltweave.portfolio.vpp.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.vpp.domain.entities.VirtualPowerPlant;
import io.voltweave.portfolio.vpp.domain.enums.VppStatus;

@Repository
public class VirtualPowerPlantRepository {
    private static final RowMapper<VirtualPowerPlant> ROW_MAPPER =
            (resultSet, rowNumber) -> new VirtualPowerPlant(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getString("name"),
                    resultSet.getString("region"),
                    VppStatus.valueOf(resultSet.getString("status")),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public VirtualPowerPlantRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(VirtualPowerPlant vpp) {
        int rows = jdbcClient.sql("""
                INSERT INTO virtual_power_plants (
                    id, organization_id, name, region, status, created_at, updated_at
                ) VALUES (
                    :id, :organizationId, :name, :region, :status, :createdAt, :updatedAt
                )
                """)
                .param("id", vpp.id())
                .param("organizationId", vpp.organizationId())
                .param("name", vpp.name())
                .param("region", vpp.region())
                .param("status", vpp.status().name())
                .param("createdAt", Timestamp.from(vpp.createdAt()))
                .param("updatedAt", Timestamp.from(vpp.updatedAt()))
                .update();
        if (rows != 1) {
            throw new IllegalStateException("Expected one inserted VPP, got " + rows);
        }
    }

    public Optional<VirtualPowerPlant> findByIdForSubject(UUID vppId, String subjectId) {
        return jdbcClient.sql("""
                SELECT v.*
                FROM virtual_power_plants v
                JOIN organization_members m ON m.organization_id = v.organization_id
                WHERE v.id = :vppId
                  AND m.subject_id = :subjectId
                  AND m.status = 'ACTIVE'
                """)
                .param("vppId", vppId)
                .param("subjectId", subjectId)
                .query(ROW_MAPPER)
                .optional();
    }
}
