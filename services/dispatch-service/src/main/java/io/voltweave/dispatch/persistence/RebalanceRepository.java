package io.voltweave.dispatch.persistence;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.dispatch.application.model.ReplacementAllocation;

@Repository
public class RebalanceRepository {
    private final JdbcClient jdbcClient;

    public RebalanceRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<UUID> activeDispatchIds(Instant now, int limit) {
        return jdbcClient.sql("""
                SELECT id FROM dispatches
                WHERE status = 'ACTIVE'
                  AND scheduled_start_at <= :now AND scheduled_end_at > :now
                ORDER BY COALESCE(under_delivery_since, :now), id
                LIMIT :limit
                """)
                .param("now", timestamp(now))
                .param("limit", limit)
                .query(UUID.class).list();
    }

    public Optional<RecoveryDispatch> lock(UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT id, organization_id, vpp_id, target_power_kw,
                       scheduled_end_at, under_delivery_since, last_rebalance_at
                FROM dispatches
                WHERE id = :dispatchId AND status = 'ACTIVE'
                FOR UPDATE
                """)
                .param("dispatchId", dispatchId)
                .query((row, rowNumber) -> new RecoveryDispatch(
                        row.getObject("id", UUID.class),
                        row.getObject("organization_id", UUID.class),
                        row.getObject("vpp_id", UUID.class),
                        row.getBigDecimal("target_power_kw"),
                        row.getTimestamp("scheduled_end_at").toInstant(),
                        instant(row.getTimestamp("under_delivery_since")),
                        instant(row.getTimestamp("last_rebalance_at"))
                )).optional();
    }

    public BigDecimal currentDeliveredPower(UUID dispatchId) {
        return jdbcClient.sql("""
                WITH devices AS (
                    SELECT device_id FROM dispatch_allocations WHERE dispatch_id = :dispatchId
                    UNION
                    SELECT device_id FROM dispatch_replacement_allocations
                    WHERE dispatch_id = :dispatchId
                )
                SELECT COALESCE(SUM(latest.delivered_power_kw), 0)
                FROM devices d
                LEFT JOIN LATERAL (
                    SELECT delivered_power_kw FROM dispatch_performance_points p
                    WHERE p.dispatch_id = :dispatchId AND p.device_id = d.device_id
                    ORDER BY observed_at DESC LIMIT 1
                ) latest ON true
                """)
                .param("dispatchId", dispatchId)
                .query(BigDecimal.class).single();
    }

    public Set<UUID> assignedDeviceIds(UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT device_id FROM dispatch_allocations WHERE dispatch_id = :dispatchId
                UNION
                SELECT device_id FROM dispatch_replacement_allocations
                WHERE dispatch_id = :dispatchId
                """)
                .param("dispatchId", dispatchId)
                .query(UUID.class).list().stream().collect(Collectors.toUnmodifiableSet());
    }

    public Set<UUID> unavailableDeviceIds(
            UUID dispatchId,
            Instant reservedFrom,
            Instant reservedUntil
    ) {
        return jdbcClient.sql("""
                SELECT device_id FROM dispatch_allocations WHERE dispatch_id = :dispatchId
                UNION
                SELECT device_id FROM dispatch_replacement_allocations
                WHERE dispatch_id = :dispatchId
                UNION
                SELECT device_id FROM device_reservations
                WHERE dispatch_id <> :dispatchId
                  AND tstzrange(reserved_from, reserved_until, '[)')
                      && tstzrange(:reservedFrom, :reservedUntil, '[)')
                """)
                .param("dispatchId", dispatchId)
                .param("reservedFrom", timestamp(reservedFrom))
                .param("reservedUntil", timestamp(reservedUntil))
                .query(UUID.class).list().stream().collect(Collectors.toUnmodifiableSet());
    }

    public void markUnderDelivery(UUID dispatchId, Instant since) {
        jdbcClient.sql("""
                UPDATE dispatches SET under_delivery_since = :since
                WHERE id = :dispatchId AND status = 'ACTIVE'
                  AND under_delivery_since IS NULL
                """)
                .param("dispatchId", dispatchId)
                .param("since", timestamp(since))
                .update();
    }

    public void clearUnderDelivery(UUID dispatchId) {
        jdbcClient.sql("""
                UPDATE dispatches SET under_delivery_since = NULL
                WHERE id = :dispatchId AND under_delivery_since IS NOT NULL
                """)
                .param("dispatchId", dispatchId)
                .update();
    }

    public UUID insertRebalance(
            RecoveryDispatch dispatch,
            UUID previewId,
            BigDecimal missingPowerKw,
            BigDecimal plannedPowerKw,
            String status,
            Instant now
    ) {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO dispatch_rebalances (
                    id, organization_id, dispatch_id, optimization_preview_id,
                    missing_power_kw, planned_power_kw, status, started_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :previewId,
                    :missingPowerKw, :plannedPowerKw, :status, :startedAt
                )
                """)
                .param("id", id)
                .param("organizationId", dispatch.organizationId())
                .param("dispatchId", dispatch.id())
                .param("previewId", previewId)
                .param("missingPowerKw", missingPowerKw)
                .param("plannedPowerKw", plannedPowerKw)
                .param("status", status)
                .param("startedAt", timestamp(now))
                .update();
        return id;
    }

    public void insertAllocations(
            RecoveryDispatch dispatch,
            UUID rebalanceId,
            List<ReplacementAllocation> allocations,
            Instant now
    ) {
        for (var value : allocations) {
            jdbcClient.sql("""
                    INSERT INTO dispatch_replacement_allocations (
                        organization_id, dispatch_id, rebalance_id, site_id, device_id,
                        device_type, source_available_power_kw, allocated_power_kw,
                        expected_energy_kwh, score, created_at
                    ) VALUES (
                        :organizationId, :dispatchId, :rebalanceId, :siteId, :deviceId,
                        :deviceType, :sourcePowerKw, :powerKw, :energyKwh, :score, :createdAt
                    )
                    """)
                    .param("organizationId", dispatch.organizationId())
                    .param("dispatchId", dispatch.id())
                    .param("rebalanceId", rebalanceId)
                    .param("siteId", value.siteId())
                    .param("deviceId", value.deviceId())
                    .param("deviceType", value.deviceType())
                    .param("sourcePowerKw", value.sourceAvailablePowerKw())
                    .param("powerKw", value.allocatedPowerKw())
                    .param("energyKwh", value.expectedEnergyKwh())
                    .param("score", value.score())
                    .param("createdAt", timestamp(now))
                    .update();
        }
    }

    public void transition(UUID dispatchId, String expected, String target, Instant now) {
        int updated = jdbcClient.sql("""
                UPDATE dispatches
                SET status = :target, version = version + 1,
                    under_delivery_since = NULL, last_rebalance_at = :now,
                    rebalance_count = rebalance_count + 1
                WHERE id = :dispatchId AND status = :expected
                """)
                .param("dispatchId", dispatchId)
                .param("expected", expected)
                .param("target", target)
                .param("now", timestamp(now))
                .update();
        if (updated != 1) {
            throw new IllegalStateException("Dispatch changed while rebalancing");
        }
    }

    public record RecoveryDispatch(
            UUID id,
            UUID organizationId,
            UUID vppId,
            BigDecimal targetPowerKw,
            Instant scheduledEndAt,
            Instant underDeliverySince,
            Instant lastRebalanceAt
    ) {
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
