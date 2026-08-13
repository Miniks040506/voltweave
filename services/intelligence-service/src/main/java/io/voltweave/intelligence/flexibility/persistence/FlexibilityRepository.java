package io.voltweave.intelligence.flexibility.persistence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.intelligence.flexibility.application.model.DeviceTelemetry;
import io.voltweave.intelligence.flexibility.application.model.FlexibilityCandidate;
import io.voltweave.intelligence.flexibility.application.model.FlexibilitySnapshot;

@Repository
public class FlexibilityRepository {
    private final JdbcClient jdbcClient;

    public FlexibilityRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Optional<DeviceTelemetry> latestTelemetry(UUID deviceId) {
        return jdbcClient.sql("""
                SELECT device_id, site_id, device_type, last_observed_at,
                       last_received_at, active_power_kw, soc_percent, online, quality
                FROM device_telemetry_projection WHERE device_id = :deviceId
                """)
                .param("deviceId", deviceId)
                .query((row, rowNumber) -> new DeviceTelemetry(
                        row.getObject("device_id", UUID.class),
                        row.getObject("site_id", UUID.class), row.getString("device_type"),
                        row.getTimestamp("last_observed_at").toInstant(),
                        row.getTimestamp("last_received_at").toInstant(),
                        row.getBigDecimal("active_power_kw"), row.getBigDecimal("soc_percent"),
                        row.getBoolean("online"), row.getString("quality")
                )).optional();
    }

    public long nextVersion(UUID organizationId, UUID vppId, Instant now) {
        return jdbcClient.sql("""
                INSERT INTO flexibility_versions (
                    vpp_organization_id, vpp_id, last_version, updated_at
                ) VALUES (:organizationId, :vppId, 1, :now)
                ON CONFLICT (vpp_organization_id, vpp_id) DO UPDATE
                SET last_version = flexibility_versions.last_version + 1,
                    updated_at = EXCLUDED.updated_at
                RETURNING last_version
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("now", timestamp(now))
                .query(Long.class).single();
    }

    public void insert(FlexibilitySnapshot snapshot) {
        jdbcClient.sql("""
                INSERT INTO flexibility_snapshots (
                    id, vpp_organization_id, vpp_id, version, dispatch_duration_seconds,
                    generated_at, valid_until, upward_flexibility_kw, available_energy_kwh
                ) VALUES (
                    :id, :organizationId, :vppId, :version, :durationSeconds,
                    :generatedAt, :validUntil, :upwardFlexibilityKw, :availableEnergyKwh
                )
                """)
                .param("id", snapshot.id())
                .param("organizationId", snapshot.organizationId())
                .param("vppId", snapshot.vppId())
                .param("version", snapshot.version())
                .param("durationSeconds", snapshot.dispatchDuration().toSeconds())
                .param("generatedAt", timestamp(snapshot.generatedAt()))
                .param("validUntil", timestamp(snapshot.validUntil()))
                .param("upwardFlexibilityKw", snapshot.upwardFlexibilityKw())
                .param("availableEnergyKwh", snapshot.availableEnergyKwh())
                .update();
        for (var candidate : snapshot.candidates()) {
            jdbcClient.sql("""
                    INSERT INTO flexibility_candidates (
                        vpp_organization_id, snapshot_id, site_id, device_id, device_type,
                        raw_upward_flexibility_kw, upward_flexibility_kw,
                        available_energy_kwh, unavailable_reason
                    ) VALUES (
                        :organizationId, :snapshotId, :siteId, :deviceId, :deviceType,
                        :rawPowerKw, :powerKw, :energyKwh, :reason
                    )
                    """)
                    .param("organizationId", snapshot.organizationId())
                    .param("snapshotId", snapshot.id())
                    .param("siteId", candidate.siteId())
                    .param("deviceId", candidate.deviceId())
                    .param("deviceType", candidate.deviceType())
                    .param("rawPowerKw", candidate.rawUpwardFlexibilityKw())
                    .param("powerKw", candidate.upwardFlexibilityKw())
                    .param("energyKwh", candidate.availableEnergyKwh())
                    .param("reason", candidate.unavailableReason())
                    .update();
        }
    }

    public Optional<FlexibilitySnapshot> latest(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT * FROM flexibility_snapshots
                WHERE vpp_organization_id = :organizationId AND vpp_id = :vppId
                ORDER BY version DESC LIMIT 1
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query((row, rowNumber) -> new SnapshotHeader(
                        row.getObject("id", UUID.class),
                        row.getObject("vpp_organization_id", UUID.class),
                        row.getObject("vpp_id", UUID.class), row.getLong("version"),
                        Duration.ofSeconds(row.getLong("dispatch_duration_seconds")),
                        row.getTimestamp("generated_at").toInstant(),
                        row.getTimestamp("valid_until").toInstant(),
                        row.getBigDecimal("upward_flexibility_kw"),
                        row.getBigDecimal("available_energy_kwh")
                )).optional().map(this::withCandidates);
    }

    private FlexibilitySnapshot withCandidates(SnapshotHeader header) {
        List<FlexibilityCandidate> candidates = jdbcClient.sql("""
                SELECT site_id, device_id, device_type, raw_upward_flexibility_kw,
                       upward_flexibility_kw, available_energy_kwh, unavailable_reason
                FROM flexibility_candidates
                WHERE vpp_organization_id = :organizationId AND snapshot_id = :snapshotId
                ORDER BY site_id, device_id
                """)
                .param("organizationId", header.organizationId())
                .param("snapshotId", header.id())
                .query((row, rowNumber) -> new FlexibilityCandidate(
                        row.getObject("site_id", UUID.class),
                        row.getObject("device_id", UUID.class), row.getString("device_type"),
                        row.getBigDecimal("raw_upward_flexibility_kw"),
                        row.getBigDecimal("upward_flexibility_kw"),
                        row.getBigDecimal("available_energy_kwh"),
                        row.getString("unavailable_reason")
                )).list();
        return new FlexibilitySnapshot(
                header.id(), header.organizationId(), header.vppId(), header.version(),
                header.dispatchDuration(), header.generatedAt(), header.validUntil(),
                header.upwardFlexibilityKw(), header.availableEnergyKwh(), candidates
        );
    }

    private record SnapshotHeader(
            UUID id, UUID organizationId, UUID vppId, long version,
            Duration dispatchDuration, Instant generatedAt, Instant validUntil,
            java.math.BigDecimal upwardFlexibilityKw, java.math.BigDecimal availableEnergyKwh
    ) {
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
