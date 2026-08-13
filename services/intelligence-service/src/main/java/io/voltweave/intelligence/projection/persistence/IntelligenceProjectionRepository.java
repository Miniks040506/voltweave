package io.voltweave.intelligence.projection.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class IntelligenceProjectionRepository {
    private final JdbcClient jdbcClient;

    public IntelligenceProjectionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean recordEventIfNew(
            String consumerName,
            UUID eventId,
            String eventType,
            Instant processedAt
    ) {
        return jdbcClient.sql("""
                INSERT INTO event_inbox (
                    consumer_name, event_id, event_type, processed_at
                ) VALUES (
                    :consumerName, :eventId, :eventType, :processedAt
                )
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)
                .param("consumerName", consumerName)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("processedAt", timestamp(processedAt))
                .update() == 1;
    }

    public void projectVppSite(
            UUID organizationId,
            UUID vppId,
            UUID siteId,
            boolean active,
            Instant updatedAt
    ) {
        jdbcClient.sql("""
                INSERT INTO vpp_site_projection (
                    vpp_organization_id, vpp_id, site_id, active, updated_at
                ) VALUES (
                    :organizationId, :vppId, :siteId, :active, :updatedAt
                )
                ON CONFLICT (vpp_id, site_id) DO UPDATE
                SET vpp_organization_id = EXCLUDED.vpp_organization_id,
                    active = EXCLUDED.active,
                    updated_at = EXCLUDED.updated_at
                WHERE vpp_site_projection.updated_at <= EXCLUDED.updated_at
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("siteId", siteId)
                .param("active", active)
                .param("updatedAt", timestamp(updatedAt))
                .update();
    }

    public void storeObservation(
            UUID organizationId,
            UUID siteId,
            UUID deviceId,
            long sequenceNumber,
            Instant observedAt,
            Instant receivedAt,
            String energyType,
            BigDecimal powerKw,
            String quality
    ) {
        jdbcClient.sql("""
                INSERT INTO energy_observations (
                    organization_id, site_id, device_id, sequence_number,
                    observed_at, received_at, energy_type, power_kw, quality
                ) VALUES (
                    :organizationId, :siteId, :deviceId, :sequenceNumber,
                    :observedAt, :receivedAt, :energyType, :powerKw, :quality
                )
                ON CONFLICT (device_id, observed_at, sequence_number) DO NOTHING
                """)
                .param("organizationId", organizationId)
                .param("siteId", siteId)
                .param("deviceId", deviceId)
                .param("sequenceNumber", sequenceNumber)
                .param("observedAt", timestamp(observedAt))
                .param("receivedAt", timestamp(receivedAt))
                .param("energyType", energyType)
                .param("powerKw", powerKw)
                .param("quality", quality)
                .update();
    }

    public void projectDeviceTelemetry(
            UUID organizationId,
            UUID siteId,
            UUID deviceId,
            String deviceType,
            Instant observedAt,
            Instant receivedAt,
            BigDecimal activePowerKw,
            BigDecimal socPercent,
            boolean online,
            String quality
    ) {
        jdbcClient.sql("""
                INSERT INTO device_telemetry_projection (
                    organization_id, site_id, device_id, device_type,
                    last_observed_at, last_received_at, active_power_kw,
                    soc_percent, online, quality, updated_at
                ) VALUES (
                    :organizationId, :siteId, :deviceId, :deviceType,
                    :observedAt, :receivedAt, :activePowerKw,
                    :socPercent, :online, :quality, :receivedAt
                )
                ON CONFLICT (device_id) DO UPDATE
                SET organization_id = EXCLUDED.organization_id,
                    site_id = EXCLUDED.site_id,
                    device_type = EXCLUDED.device_type,
                    last_observed_at = EXCLUDED.last_observed_at,
                    last_received_at = EXCLUDED.last_received_at,
                    active_power_kw = EXCLUDED.active_power_kw,
                    soc_percent = EXCLUDED.soc_percent,
                    online = EXCLUDED.online,
                    quality = EXCLUDED.quality,
                    updated_at = EXCLUDED.updated_at
                WHERE (device_telemetry_projection.last_observed_at,
                       device_telemetry_projection.last_received_at)
                    <= (EXCLUDED.last_observed_at, EXCLUDED.last_received_at)
                """)
                .param("organizationId", organizationId)
                .param("siteId", siteId)
                .param("deviceId", deviceId)
                .param("deviceType", deviceType)
                .param("observedAt", timestamp(observedAt))
                .param("receivedAt", timestamp(receivedAt))
                .param("activePowerKw", activePowerKw)
                .param("socPercent", socPercent)
                .param("online", online)
                .param("quality", quality)
                .update();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
