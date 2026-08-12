package io.voltweave.telemetry.query.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.telemetry.query.application.model.DeviceTwin;
import io.voltweave.telemetry.query.application.model.TelemetryPoint;

@Repository
public class TelemetryQueryRepository {
    private final JdbcClient jdbcClient;

    public TelemetryQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<TelemetryPoint> findSiteHistory(
            UUID organizationId,
            UUID siteId,
            Instant from,
            Instant to,
            int limit
    ) {
        return jdbcClient.sql("""
                SELECT device_id, sequence_number, observed_at, received_at,
                       device_type, active_power_kw, soc_percent, online,
                       telemetry_quality
                FROM telemetry_points
                WHERE organization_id = :organizationId
                  AND site_id = :siteId
                  AND observed_at >= :from
                  AND observed_at < :to
                ORDER BY observed_at DESC, device_id, sequence_number DESC
                LIMIT :limit
                """)
                .param("organizationId", organizationId)
                .param("siteId", siteId)
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .param("limit", limit)
                .query((resultSet, rowNumber) -> new TelemetryPoint(
                        resultSet.getObject("device_id", UUID.class),
                        resultSet.getLong("sequence_number"),
                        resultSet.getTimestamp("observed_at").toInstant(),
                        resultSet.getTimestamp("received_at").toInstant(),
                        resultSet.getString("device_type"),
                        resultSet.getBigDecimal("active_power_kw"),
                        resultSet.getBigDecimal("soc_percent"),
                        resultSet.getBoolean("online"),
                        resultSet.getString("telemetry_quality")
                ))
                .list();
    }

    public List<DeviceTwin> findSiteTwins(UUID organizationId, UUID siteId) {
        return jdbcClient.sql("""
                SELECT site_id, device_id, device_type, last_sequence_number,
                       last_observed_at, last_received_at, active_power_kw,
                       soc_percent, online, telemetry_quality, updated_at
                FROM device_twins
                WHERE organization_id = :organizationId AND site_id = :siteId
                ORDER BY device_id
                """)
                .param("organizationId", organizationId)
                .param("siteId", siteId)
                .query((resultSet, rowNumber) -> twin(resultSet))
                .list();
    }

    public Optional<DeviceTwin> findDeviceTwin(UUID organizationId, UUID deviceId) {
        return jdbcClient.sql("""
                SELECT site_id, device_id, device_type, last_sequence_number,
                       last_observed_at, last_received_at, active_power_kw,
                       soc_percent, online, telemetry_quality, updated_at
                FROM device_twins
                WHERE organization_id = :organizationId AND device_id = :deviceId
                """)
                .param("organizationId", organizationId)
                .param("deviceId", deviceId)
                .query((resultSet, rowNumber) -> twin(resultSet))
                .optional();
    }

    private static DeviceTwin twin(java.sql.ResultSet resultSet) throws java.sql.SQLException {
        return new DeviceTwin(
                resultSet.getObject("site_id", UUID.class),
                resultSet.getObject("device_id", UUID.class),
                resultSet.getString("device_type"),
                resultSet.getLong("last_sequence_number"),
                resultSet.getTimestamp("last_observed_at").toInstant(),
                resultSet.getTimestamp("last_received_at").toInstant(),
                resultSet.getBigDecimal("active_power_kw"),
                resultSet.getBigDecimal("soc_percent"),
                resultSet.getBoolean("online"),
                resultSet.getString("telemetry_quality"),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
