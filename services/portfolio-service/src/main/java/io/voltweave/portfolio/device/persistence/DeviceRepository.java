package io.voltweave.portfolio.device.persistence;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.device.domain.entities.Device;
import io.voltweave.portfolio.device.domain.enums.CommunicationProtocol;
import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.domain.enums.DeviceType;

@Repository
public class DeviceRepository {
    private static final RowMapper<Device> ROW_MAPPER = (resultSet, rowNumber) ->
            new Device(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getObject("site_id", UUID.class),
                    resultSet.getString("external_device_id"),
                    DeviceType.valueOf(resultSet.getString("type")),
                    resultSet.getString("manufacturer"),
                    resultSet.getString("model"),
                    resultSet.getBigDecimal("rated_power_kw"),
                    DeviceLifecycleStatus.valueOf(resultSet.getString("status")),
                    CommunicationProtocol.valueOf(
                            resultSet.getString("communication_protocol")
                    ),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public DeviceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(Device device) {
        int rows = jdbcClient.sql("""
                INSERT INTO devices (
                    id, organization_id, site_id, external_device_id, type,
                    manufacturer, model, rated_power_kw, status,
                    communication_protocol, created_at, updated_at
                ) VALUES (
                    :id, :organizationId, :siteId, :externalDeviceId, :type,
                    :manufacturer, :model, :ratedPowerKw, :status,
                    :communicationProtocol, :createdAt, :updatedAt
                )
                """)
                .param("id", device.id())
                .param("organizationId", device.organizationId())
                .param("siteId", device.siteId())
                .param("externalDeviceId", device.externalDeviceId())
                .param("type", device.type().name())
                .param("manufacturer", device.manufacturer())
                .param("model", device.model())
                .param("ratedPowerKw", device.ratedPowerKw())
                .param("status", device.status().name())
                .param("communicationProtocol", device.communicationProtocol().name())
                .param("createdAt", Timestamp.from(device.createdAt()))
                .param("updatedAt", Timestamp.from(device.updatedAt()))
                .update();

        requireOneRow(rows, "inserted");
    }

    public void update(Device device) {
        int rows = jdbcClient.sql("""
                UPDATE devices
                SET external_device_id = :externalDeviceId,
                    manufacturer = :manufacturer,
                    model = :model,
                    rated_power_kw = :ratedPowerKw,
                    status = :status,
                    updated_at = :updatedAt
                WHERE id = :id AND organization_id = :organizationId
                """)
                .param("id", device.id())
                .param("organizationId", device.organizationId())
                .param("externalDeviceId", device.externalDeviceId())
                .param("manufacturer", device.manufacturer())
                .param("model", device.model())
                .param("ratedPowerKw", device.ratedPowerKw())
                .param("status", device.status().name())
                .param("updatedAt", Timestamp.from(device.updatedAt()))
                .update();

        requireOneRow(rows, "updated");
    }

    public Optional<Device> findByIdForSubject(UUID deviceId, String subjectId) {
        return jdbcClient.sql("""
                SELECT d.*
                FROM devices d
                JOIN organization_members m ON m.organization_id = d.organization_id
                WHERE d.id = :deviceId
                  AND m.subject_id = :subjectId
                  AND m.status = 'ACTIVE'
                """)
                .param("deviceId", deviceId)
                .param("subjectId", subjectId)
                .query(ROW_MAPPER)
                .optional();
    }

    public List<Device> findBySiteIdForSubject(UUID siteId, String subjectId) {
        return jdbcClient.sql("""
                SELECT d.*
                FROM devices d
                JOIN organization_members m ON m.organization_id = d.organization_id
                WHERE d.site_id = :siteId
                  AND m.subject_id = :subjectId
                  AND m.status = 'ACTIVE'
                ORDER BY d.created_at, d.id
                """)
                .param("siteId", siteId)
                .param("subjectId", subjectId)
                .query(ROW_MAPPER)
                .list();
    }

    private static void requireOneRow(int rows, String action) {
        if (rows != 1) {
            throw new IllegalStateException("Expected one " + action + " device, got " + rows);
        }
    }
}
