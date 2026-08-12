package io.voltweave.portfolio.device.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.device.domain.entities.DeviceProvisioningRequest;
import io.voltweave.portfolio.device.domain.enums.ProvisioningStatus;

@Repository
public class DeviceProvisioningRepository {
    private static final RowMapper<DeviceProvisioningRequest> ROW_MAPPER =
            (resultSet, rowNumber) -> new DeviceProvisioningRequest(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getObject("device_id", UUID.class),
                    ProvisioningStatus.valueOf(resultSet.getString("status")),
                    resultSet.getString("mqtt_username"),
                    resultSet.getString("mqtt_client_id"),
                    timestamp(resultSet.getTimestamp("provisioned_at")),
                    timestamp(resultSet.getTimestamp("revoked_at")),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public DeviceProvisioningRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(DeviceProvisioningRequest request) {
        int rows = jdbcClient.sql("""
                INSERT INTO device_provisioning_requests (
                    id, organization_id, device_id, status, created_at, updated_at
                ) VALUES (
                    :id, :organizationId, :deviceId, :status, :createdAt, :updatedAt
                )
                """)
                .param("id", request.id())
                .param("organizationId", request.organizationId())
                .param("deviceId", request.deviceId())
                .param("status", request.status().name())
                .param("createdAt", Timestamp.from(request.createdAt()))
                .param("updatedAt", Timestamp.from(request.updatedAt()))
                .update();
        if (rows != 1) {
            throw new IllegalStateException("Expected one inserted provisioning request");
        }
    }

    public Optional<DeviceProvisioningRequest> findById(UUID organizationId, UUID requestId) {
        return jdbcClient.sql("""
                SELECT * FROM device_provisioning_requests
                WHERE organization_id = :organizationId AND id = :requestId
                """)
                .param("organizationId", organizationId)
                .param("requestId", requestId)
                .query(ROW_MAPPER)
                .optional();
    }

    public Optional<DeviceProvisioningRequest> findByDevice(
            UUID organizationId,
            UUID deviceId
    ) {
        return jdbcClient.sql("""
                SELECT * FROM device_provisioning_requests
                WHERE organization_id = :organizationId AND device_id = :deviceId
                """)
                .param("organizationId", organizationId)
                .param("deviceId", deviceId)
                .query(ROW_MAPPER)
                .optional();
    }

    public void update(DeviceProvisioningRequest request) {
        int rows = jdbcClient.sql("""
                UPDATE device_provisioning_requests
                SET status = :status,
                    mqtt_username = :mqttUsername,
                    mqtt_client_id = :mqttClientId,
                    provisioned_at = :provisionedAt,
                    revoked_at = :revokedAt,
                    updated_at = :updatedAt
                WHERE organization_id = :organizationId AND id = :id
                """)
                .param("id", request.id())
                .param("organizationId", request.organizationId())
                .param("status", request.status().name())
                .param("mqttUsername", request.mqttUsername())
                .param("mqttClientId", request.mqttClientId())
                .param("provisionedAt", request.provisionedAt() == null
                        ? null : Timestamp.from(request.provisionedAt()))
                .param("revokedAt", request.revokedAt() == null
                        ? null : Timestamp.from(request.revokedAt()))
                .param("updatedAt", Timestamp.from(request.updatedAt()))
                .update();
        if (rows != 1) {
            throw new IllegalStateException("Expected one updated provisioning request");
        }
    }

    private static java.time.Instant timestamp(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
