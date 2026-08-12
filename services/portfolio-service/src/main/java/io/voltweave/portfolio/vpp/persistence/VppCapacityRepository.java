package io.voltweave.portfolio.vpp.persistence;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.vpp.domain.entities.VppInstalledCapacity;

@Repository
public class VppCapacityRepository {
    private final JdbcClient jdbcClient;

    public VppCapacityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public VppInstalledCapacity calculate(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT
                    count(DISTINCT vm.site_id) AS site_count,
                    count(d.id) AS device_count,
                    coalesce(sum(d.rated_power_kw) FILTER (
                        WHERE d.type = 'SOLAR_INVERTER'
                    ), 0) AS solar_power_kw,
                    coalesce(sum(d.rated_power_kw) FILTER (
                        WHERE d.type = 'BATTERY'
                    ), 0) AS battery_power_kw,
                    coalesce(sum(d.rated_power_kw) FILTER (
                        WHERE d.type = 'EV_CHARGER'
                    ), 0) AS ev_charger_power_kw
                FROM virtual_power_plants v
                LEFT JOIN vpp_memberships vm
                  ON vm.vpp_organization_id = v.organization_id
                 AND vm.vpp_id = v.id
                 AND vm.status = 'ACTIVE'
                LEFT JOIN devices d
                  ON d.organization_id = vm.site_organization_id
                 AND d.site_id = vm.site_id
                 AND d.status NOT IN ('DISABLED', 'RETIRED')
                 AND d.type <> 'SMART_METER'
                WHERE v.organization_id = :organizationId AND v.id = :vppId
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query((resultSet, rowNumber) -> new VppInstalledCapacity(
                        resultSet.getLong("site_count"),
                        resultSet.getLong("device_count"),
                        resultSet.getBigDecimal("solar_power_kw"),
                        resultSet.getBigDecimal("battery_power_kw"),
                        resultSet.getBigDecimal("ev_charger_power_kw")
                ))
                .single();
    }
}
