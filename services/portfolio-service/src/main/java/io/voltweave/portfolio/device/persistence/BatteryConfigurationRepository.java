package io.voltweave.portfolio.device.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.device.domain.entities.BatteryConfiguration;

@Repository
public class BatteryConfigurationRepository {
    private static final RowMapper<BatteryConfiguration> ROW_MAPPER =
            (resultSet, rowNumber) -> new BatteryConfiguration(
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getObject("device_id", UUID.class),
                    resultSet.getBigDecimal("capacity_kwh"),
                    resultSet.getBigDecimal("max_charge_kw"),
                    resultSet.getBigDecimal("max_discharge_kw"),
                    resultSet.getInt("min_soc_percent"),
                    resultSet.getInt("max_soc_percent"),
                    resultSet.getBigDecimal("efficiency"),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public BatteryConfigurationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(BatteryConfiguration configuration) {
        int rows = jdbcClient.sql("""
                INSERT INTO battery_configurations (
                    device_id, organization_id, capacity_kwh, max_charge_kw,
                    max_discharge_kw, min_soc_percent, max_soc_percent,
                    efficiency, updated_at
                ) VALUES (
                    :deviceId, :organizationId, :capacityKwh, :maxChargeKw,
                    :maxDischargeKw, :minSocPercent, :maxSocPercent,
                    :efficiency, :updatedAt
                )
                """)
                .param("deviceId", configuration.deviceId())
                .param("organizationId", configuration.organizationId())
                .param("capacityKwh", configuration.capacityKwh())
                .param("maxChargeKw", configuration.maxChargeKw())
                .param("maxDischargeKw", configuration.maxDischargeKw())
                .param("minSocPercent", configuration.minSocPercent())
                .param("maxSocPercent", configuration.maxSocPercent())
                .param("efficiency", configuration.efficiency())
                .param("updatedAt", Timestamp.from(configuration.updatedAt()))
                .update();

        requireOneRow(rows, "inserted");
    }

    public void update(BatteryConfiguration configuration) {
        int rows = jdbcClient.sql("""
                UPDATE battery_configurations
                SET capacity_kwh = :capacityKwh,
                    max_charge_kw = :maxChargeKw,
                    max_discharge_kw = :maxDischargeKw,
                    min_soc_percent = :minSocPercent,
                    max_soc_percent = :maxSocPercent,
                    efficiency = :efficiency,
                    updated_at = :updatedAt
                WHERE device_id = :deviceId AND organization_id = :organizationId
                """)
                .param("deviceId", configuration.deviceId())
                .param("organizationId", configuration.organizationId())
                .param("capacityKwh", configuration.capacityKwh())
                .param("maxChargeKw", configuration.maxChargeKw())
                .param("maxDischargeKw", configuration.maxDischargeKw())
                .param("minSocPercent", configuration.minSocPercent())
                .param("maxSocPercent", configuration.maxSocPercent())
                .param("efficiency", configuration.efficiency())
                .param("updatedAt", Timestamp.from(configuration.updatedAt()))
                .update();

        requireOneRow(rows, "updated");
    }

    public Optional<BatteryConfiguration> findByDeviceId(UUID organizationId, UUID deviceId) {
        return jdbcClient.sql("""
                SELECT * FROM battery_configurations
                WHERE organization_id = :organizationId AND device_id = :deviceId
                """)
                .param("organizationId", organizationId)
                .param("deviceId", deviceId)
                .query(ROW_MAPPER)
                .optional();
    }

    private static void requireOneRow(int rows, String action) {
        if (rows != 1) {
            throw new IllegalStateException(
                    "Expected one " + action + " battery configuration, got " + rows
            );
        }
    }
}
