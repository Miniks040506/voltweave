package io.voltweave.dispatch.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.dispatch.application.model.DispatchPerformance;

@Repository
public class PerformanceRepository {
    public static final String CONSUMER_NAME = "dispatch-performance-v1";

    private final JdbcClient jdbcClient;

    public PerformanceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean recordEventIfNew(UUID eventId, String eventType, Instant receivedAt) {
        return jdbcClient.sql("""
                INSERT INTO event_inbox (consumer_name, event_id, event_type, received_at)
                VALUES (:consumer, :eventId, :eventType, :receivedAt)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)
                .param("consumer", CONSUMER_NAME)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("receivedAt", timestamp(receivedAt))
                .update() == 1;
    }

    public List<ActiveAllocation> findActiveAllocations(
            UUID organizationId,
            UUID siteId,
            UUID deviceId,
            Instant observedAt
    ) {
        return jdbcClient.sql("""
                WITH allocations AS (
                    SELECT organization_id, dispatch_id, site_id, device_id, device_type,
                           source_available_power_kw, allocated_power_kw
                    FROM dispatch_allocations
                    UNION ALL
                    SELECT organization_id, dispatch_id, site_id, device_id, device_type,
                           source_available_power_kw, allocated_power_kw
                    FROM dispatch_replacement_allocations
                )
                SELECT a.dispatch_id, a.device_type, a.source_available_power_kw,
                       a.allocated_power_kw, c.target_power_kw
                FROM allocations a
                JOIN dispatches d ON d.id = a.dispatch_id
                    AND d.organization_id = a.organization_id
                JOIN device_commands c ON c.dispatch_id = a.dispatch_id
                    AND c.device_id = a.device_id
                WHERE a.organization_id = :organizationId
                  AND a.site_id = :siteId AND a.device_id = :deviceId
                  AND d.status IN ('ACTIVE', 'REBALANCING')
                  AND d.scheduled_start_at <= :observedAt
                  AND d.scheduled_end_at >= :observedAt
                  AND c.status = 'ACCEPTED'
                ORDER BY a.dispatch_id
                """)
                .param("organizationId", organizationId)
                .param("siteId", siteId)
                .param("deviceId", deviceId)
                .param("observedAt", timestamp(observedAt))
                .query((row, rowNumber) -> new ActiveAllocation(
                        row.getObject("dispatch_id", UUID.class),
                        row.getString("device_type"),
                        row.getBigDecimal("source_available_power_kw"),
                        row.getBigDecimal("allocated_power_kw"),
                        row.getBigDecimal("target_power_kw")
                )).list();
    }

    public LastPoint lastPoint(UUID dispatchId, UUID deviceId) {
        return jdbcClient.sql("""
                SELECT observed_at, delivered_power_kw, cumulative_delivered_energy_kwh
                FROM dispatch_performance_points
                WHERE dispatch_id = :dispatchId AND device_id = :deviceId
                ORDER BY observed_at DESC LIMIT 1 FOR UPDATE
                """)
                .param("dispatchId", dispatchId)
                .param("deviceId", deviceId)
                .query((row, rowNumber) -> new LastPoint(
                        row.getTimestamp("observed_at").toInstant(),
                        row.getBigDecimal("delivered_power_kw"),
                        row.getBigDecimal("cumulative_delivered_energy_kwh")
                )).optional().orElse(null);
    }

    public void insert(PerformancePoint point) {
        jdbcClient.sql("""
                INSERT INTO dispatch_performance_points (
                    id, organization_id, dispatch_id, site_id, device_id,
                    telemetry_event_id, observed_at, target_power_kw,
                    requested_power_kw, actual_power_kw, delivered_power_kw,
                    error_kw, error_percent, cumulative_delivered_energy_kwh,
                    online, quality, recorded_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :siteId, :deviceId,
                    :eventId, :observedAt, :targetPowerKw,
                    :requestedPowerKw, :actualPowerKw, :deliveredPowerKw,
                    :errorKw, :errorPercent, :energyKwh,
                    :online, 'VALID', :recordedAt
                )
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", point.organizationId())
                .param("dispatchId", point.dispatchId())
                .param("siteId", point.siteId())
                .param("deviceId", point.deviceId())
                .param("eventId", point.eventId())
                .param("observedAt", timestamp(point.observedAt()))
                .param("targetPowerKw", point.targetPowerKw())
                .param("requestedPowerKw", point.requestedPowerKw())
                .param("actualPowerKw", point.actualPowerKw())
                .param("deliveredPowerKw", point.deliveredPowerKw())
                .param("errorKw", point.errorKw())
                .param("errorPercent", point.errorPercent())
                .param("energyKwh", point.cumulativeDeliveredEnergyKwh())
                .param("online", point.online())
                .param("recordedAt", timestamp(point.recordedAt()))
                .update();
    }

    public List<DispatchPerformance.Point> findPoints(UUID organizationId, UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT device_id, observed_at, target_power_kw, requested_power_kw,
                       actual_power_kw, delivered_power_kw, error_kw, error_percent,
                       cumulative_delivered_energy_kwh, online
                FROM dispatch_performance_points
                WHERE organization_id = :organizationId AND dispatch_id = :dispatchId
                ORDER BY observed_at, device_id
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .query((row, rowNumber) -> new DispatchPerformance.Point(
                        row.getObject("device_id", UUID.class),
                        row.getTimestamp("observed_at").toInstant(),
                        row.getBigDecimal("target_power_kw"),
                        row.getBigDecimal("requested_power_kw"),
                        row.getBigDecimal("actual_power_kw"),
                        row.getBigDecimal("delivered_power_kw"),
                        row.getBigDecimal("error_kw"),
                        row.getBigDecimal("error_percent"),
                        row.getBigDecimal("cumulative_delivered_energy_kwh"),
                        row.getBoolean("online")
                )).list();
    }

    public BigDecimal originalRequestedPower(UUID organizationId, UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT COALESCE(SUM(allocated_power_kw), 0)
                FROM dispatch_allocations
                WHERE organization_id = :organizationId AND dispatch_id = :dispatchId
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .query(BigDecimal.class)
                .single();
    }

    public record ActiveAllocation(
            UUID dispatchId,
            String deviceType,
            BigDecimal sourceAvailablePowerKw,
            BigDecimal requestedPowerKw,
            BigDecimal targetPowerKw
    ) {
    }

    public record LastPoint(
            Instant observedAt,
            BigDecimal deliveredPowerKw,
            BigDecimal cumulativeDeliveredEnergyKwh
    ) {
    }

    public record PerformancePoint(
            UUID organizationId,
            UUID dispatchId,
            UUID siteId,
            UUID deviceId,
            UUID eventId,
            Instant observedAt,
            BigDecimal targetPowerKw,
            BigDecimal requestedPowerKw,
            BigDecimal actualPowerKw,
            BigDecimal deliveredPowerKw,
            BigDecimal errorKw,
            BigDecimal errorPercent,
            BigDecimal cumulativeDeliveredEnergyKwh,
            boolean online,
            Instant recordedAt
    ) {
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
