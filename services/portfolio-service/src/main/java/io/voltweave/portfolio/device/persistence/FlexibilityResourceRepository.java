package io.voltweave.portfolio.device.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.device.application.model.FlexibilityResource;
import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.domain.enums.DeviceType;

@Repository
public class FlexibilityResourceRepository {
    private final JdbcClient jdbcClient;

    public FlexibilityResourceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<FlexibilityResource> findByVppId(UUID vppId) {
        return jdbcClient.sql("""
                SELECT d.organization_id, d.site_id, d.id AS device_id,
                       d.type, d.status, p.vpp_opt_in, d.rated_power_kw,
                       b.capacity_kwh, b.max_discharge_kw,
                       GREATEST(b.min_soc_percent,
                                p.minimum_battery_reserve_percent) AS minimum_soc_percent,
                       b.efficiency AS discharge_efficiency,
                       e.max_charging_kw, e.vehicle_battery_capacity_kwh,
                       e.target_soc_percent, e.charging_efficiency, e.departure_at
                FROM vpp_memberships membership
                JOIN site_preferences p
                  ON p.organization_id = membership.site_organization_id
                 AND p.site_id = membership.site_id
                JOIN devices d
                  ON d.organization_id = membership.site_organization_id
                 AND d.site_id = membership.site_id
                LEFT JOIN battery_configurations b
                  ON b.organization_id = d.organization_id AND b.device_id = d.id
                LEFT JOIN ev_charger_configurations e
                  ON e.organization_id = d.organization_id AND e.device_id = d.id
                WHERE membership.vpp_id = :vppId
                  AND membership.status = 'ACTIVE'
                  AND d.type IN ('BATTERY', 'EV_CHARGER')
                ORDER BY d.site_id, d.id
                """)
                .param("vppId", vppId)
                .query((resultSet, rowNumber) -> new FlexibilityResource(
                        resultSet.getObject("organization_id", UUID.class),
                        resultSet.getObject("site_id", UUID.class),
                        resultSet.getObject("device_id", UUID.class),
                        DeviceType.valueOf(resultSet.getString("type")),
                        DeviceLifecycleStatus.valueOf(resultSet.getString("status")),
                        resultSet.getBoolean("vpp_opt_in"),
                        resultSet.getBigDecimal("rated_power_kw"),
                        resultSet.getBigDecimal("capacity_kwh"),
                        resultSet.getBigDecimal("max_discharge_kw"),
                        resultSet.getObject("minimum_soc_percent", Integer.class),
                        resultSet.getBigDecimal("discharge_efficiency"),
                        resultSet.getBigDecimal("max_charging_kw"),
                        resultSet.getBigDecimal("vehicle_battery_capacity_kwh"),
                        resultSet.getObject("target_soc_percent", Integer.class),
                        resultSet.getBigDecimal("charging_efficiency"),
                        resultSet.getTimestamp("departure_at") == null ? null
                                : resultSet.getTimestamp("departure_at").toInstant()
                ))
                .list();
    }
}
