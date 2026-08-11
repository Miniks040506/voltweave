package io.voltweave.portfolio.device.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.device.domain.entities.EvChargerConfiguration;

@Repository
public class EvChargerConfigurationRepository {
    private static final RowMapper<EvChargerConfiguration> ROW_MAPPER =
            (resultSet, rowNumber) -> new EvChargerConfiguration(
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getObject("device_id", UUID.class),
                    resultSet.getBigDecimal("max_charging_kw"),
                    resultSet.getBigDecimal("vehicle_battery_capacity_kwh"),
                    resultSet.getInt("target_soc_percent"),
                    resultSet.getBigDecimal("charging_efficiency"),
                    resultSet.getTimestamp("departure_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public EvChargerConfigurationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(EvChargerConfiguration configuration) {
        int rows = jdbcClient.sql("""
                INSERT INTO ev_charger_configurations (
                    device_id, organization_id, max_charging_kw,
                    vehicle_battery_capacity_kwh, target_soc_percent,
                    charging_efficiency, departure_at, updated_at
                ) VALUES (
                    :deviceId, :organizationId, :maxChargingKw,
                    :vehicleBatteryCapacityKwh, :targetSocPercent,
                    :chargingEfficiency, :departureAt, :updatedAt
                )
                """)
                .param("deviceId", configuration.deviceId())
                .param("organizationId", configuration.organizationId())
                .param("maxChargingKw", configuration.maxChargingKw())
                .param("vehicleBatteryCapacityKwh", configuration.vehicleBatteryCapacityKwh())
                .param("targetSocPercent", configuration.targetSocPercent())
                .param("chargingEfficiency", configuration.chargingEfficiency())
                .param("departureAt", Timestamp.from(configuration.departureAt()))
                .param("updatedAt", Timestamp.from(configuration.updatedAt()))
                .update();

        requireOneRow(rows, "inserted");
    }

    public void update(EvChargerConfiguration configuration) {
        int rows = jdbcClient.sql("""
                UPDATE ev_charger_configurations
                SET max_charging_kw = :maxChargingKw,
                    vehicle_battery_capacity_kwh = :vehicleBatteryCapacityKwh,
                    target_soc_percent = :targetSocPercent,
                    charging_efficiency = :chargingEfficiency,
                    departure_at = :departureAt,
                    updated_at = :updatedAt
                WHERE device_id = :deviceId AND organization_id = :organizationId
                """)
                .param("deviceId", configuration.deviceId())
                .param("organizationId", configuration.organizationId())
                .param("maxChargingKw", configuration.maxChargingKw())
                .param("vehicleBatteryCapacityKwh", configuration.vehicleBatteryCapacityKwh())
                .param("targetSocPercent", configuration.targetSocPercent())
                .param("chargingEfficiency", configuration.chargingEfficiency())
                .param("departureAt", Timestamp.from(configuration.departureAt()))
                .param("updatedAt", Timestamp.from(configuration.updatedAt()))
                .update();

        requireOneRow(rows, "updated");
    }

    public Optional<EvChargerConfiguration> findByDeviceId(
            UUID organizationId,
            UUID deviceId
    ) {
        return jdbcClient.sql("""
                SELECT * FROM ev_charger_configurations
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
                    "Expected one " + action + " EV charger configuration, got " + rows
            );
        }
    }
}
