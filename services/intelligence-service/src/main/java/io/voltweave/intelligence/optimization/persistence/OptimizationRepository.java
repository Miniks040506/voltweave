package io.voltweave.intelligence.optimization.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.intelligence.domain.WeightedAllocator.Weights;
import io.voltweave.intelligence.optimization.application.model.OptimizationCandidate;
import io.voltweave.intelligence.optimization.application.model.OptimizationPreview;

@Repository
public class OptimizationRepository {
    private final JdbcClient jdbcClient;

    public OptimizationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long nextVersion(UUID organizationId, UUID vppId, Instant now) {
        return jdbcClient.sql("""
                INSERT INTO optimization_versions (
                    vpp_organization_id, vpp_id, last_version, updated_at
                ) VALUES (:organizationId, :vppId, 1, :now)
                ON CONFLICT (vpp_organization_id, vpp_id) DO UPDATE
                SET last_version = optimization_versions.last_version + 1,
                    updated_at = EXCLUDED.updated_at
                RETURNING last_version
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("now", Timestamp.from(now))
                .query(Long.class).single();
    }

    public void insert(OptimizationPreview preview, Weights weights) {
        jdbcClient.sql("""
                INSERT INTO optimization_previews (
                    id, vpp_organization_id, vpp_id, version, flexibility_snapshot_id,
                    flexibility_snapshot_version, target_power_kw, reserve_margin_percent,
                    required_power_kw, planned_power_kw, feasible, weight_version,
                    reliability_weight, available_soc_weight, response_speed_weight,
                    low_degradation_cost_weight, customer_preference_weight, created_at
                ) VALUES (
                    :id, :organizationId, :vppId, :version, :snapshotId,
                    :snapshotVersion, :targetPowerKw, :reserveMarginPercent,
                    :requiredPowerKw, :plannedPowerKw, :feasible, :weightVersion,
                    :reliabilityWeight, :availableSocWeight, :responseSpeedWeight,
                    :degradationWeight, :preferenceWeight, :createdAt
                )
                """)
                .param("id", preview.id())
                .param("organizationId", preview.organizationId())
                .param("vppId", preview.vppId())
                .param("version", preview.version())
                .param("snapshotId", preview.flexibilitySnapshotId())
                .param("snapshotVersion", preview.flexibilitySnapshotVersion())
                .param("targetPowerKw", preview.targetPowerKw())
                .param("reserveMarginPercent", preview.reserveMarginPercent())
                .param("requiredPowerKw", preview.requiredPowerKw())
                .param("plannedPowerKw", preview.plannedPowerKw())
                .param("feasible", preview.feasible())
                .param("weightVersion", preview.weightVersion())
                .param("reliabilityWeight", weights.reliability())
                .param("availableSocWeight", weights.availableSoc())
                .param("responseSpeedWeight", weights.responseSpeed())
                .param("degradationWeight", weights.lowDegradationCost())
                .param("preferenceWeight", weights.customerPreference())
                .param("createdAt", Timestamp.from(preview.createdAt()))
                .update();

        for (var candidate : preview.candidates()) {
            jdbcClient.sql("""
                    INSERT INTO optimization_candidates (
                        vpp_organization_id, preview_id, site_id, device_id, device_type,
                        available_power_kw, available_energy_kwh, reliability, available_soc,
                        response_speed, low_degradation_cost, customer_preference, score,
                        allocated_power_kw, eligible
                    ) VALUES (
                        :organizationId, :previewId, :siteId, :deviceId, :deviceType,
                        :availablePowerKw, :availableEnergyKwh, :reliability, :availableSoc,
                        :responseSpeed, :degradationCost, :preference, :score,
                        :allocatedPowerKw, :eligible
                    )
                    """)
                    .param("organizationId", preview.organizationId())
                    .param("previewId", preview.id())
                    .param("siteId", candidate.siteId())
                    .param("deviceId", candidate.deviceId())
                    .param("deviceType", candidate.deviceType())
                    .param("availablePowerKw", candidate.availablePowerKw())
                    .param("availableEnergyKwh", candidate.availableEnergyKwh())
                    .param("reliability", candidate.reliability())
                    .param("availableSoc", candidate.availableSoc())
                    .param("responseSpeed", candidate.responseSpeed())
                    .param("degradationCost", candidate.lowDegradationCost())
                    .param("preference", candidate.customerPreference())
                    .param("score", candidate.score())
                    .param("allocatedPowerKw", candidate.allocatedPowerKw())
                    .param("eligible", candidate.eligible())
                    .update();
        }
    }

    public Optional<OptimizationPreview> latest(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT * FROM optimization_previews
                WHERE vpp_organization_id = :organizationId AND vpp_id = :vppId
                ORDER BY version DESC LIMIT 1
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query((row, rowNumber) -> new PreviewHeader(
                        row.getObject("id", UUID.class),
                        row.getObject("vpp_organization_id", UUID.class),
                        row.getObject("vpp_id", UUID.class), row.getLong("version"),
                        row.getObject("flexibility_snapshot_id", UUID.class),
                        row.getLong("flexibility_snapshot_version"),
                        row.getBigDecimal("target_power_kw"),
                        row.getBigDecimal("reserve_margin_percent"),
                        row.getBigDecimal("required_power_kw"),
                        row.getBigDecimal("planned_power_kw"), row.getBoolean("feasible"),
                        row.getString("weight_version"), row.getTimestamp("created_at").toInstant()
                )).optional().map(this::withCandidates);
    }

    public Optional<OptimizationPreview> find(
            UUID organizationId,
            UUID vppId,
            UUID previewId
    ) {
        return jdbcClient.sql("""
                SELECT * FROM optimization_previews
                WHERE vpp_organization_id = :organizationId
                  AND vpp_id = :vppId AND id = :previewId
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("previewId", previewId)
                .query((row, rowNumber) -> new PreviewHeader(
                        row.getObject("id", UUID.class),
                        row.getObject("vpp_organization_id", UUID.class),
                        row.getObject("vpp_id", UUID.class), row.getLong("version"),
                        row.getObject("flexibility_snapshot_id", UUID.class),
                        row.getLong("flexibility_snapshot_version"),
                        row.getBigDecimal("target_power_kw"),
                        row.getBigDecimal("reserve_margin_percent"),
                        row.getBigDecimal("required_power_kw"),
                        row.getBigDecimal("planned_power_kw"), row.getBoolean("feasible"),
                        row.getString("weight_version"), row.getTimestamp("created_at").toInstant()
                )).optional().map(this::withCandidates);
    }

    private OptimizationPreview withCandidates(PreviewHeader header) {
        List<OptimizationCandidate> candidates = jdbcClient.sql("""
                SELECT * FROM optimization_candidates
                WHERE vpp_organization_id = :organizationId AND preview_id = :previewId
                ORDER BY allocated_power_kw DESC, score DESC, device_id
                """)
                .param("organizationId", header.organizationId())
                .param("previewId", header.id())
                .query((row, rowNumber) -> new OptimizationCandidate(
                        row.getObject("site_id", UUID.class),
                        row.getObject("device_id", UUID.class), row.getString("device_type"),
                        row.getBigDecimal("available_power_kw"),
                        row.getBigDecimal("available_energy_kwh"),
                        row.getBigDecimal("reliability"), row.getBigDecimal("available_soc"),
                        row.getBigDecimal("response_speed"),
                        row.getBigDecimal("low_degradation_cost"),
                        row.getBigDecimal("customer_preference"), row.getBigDecimal("score"),
                        row.getBigDecimal("allocated_power_kw"), row.getBoolean("eligible")
                )).list();
        return new OptimizationPreview(
                header.id(), header.organizationId(), header.vppId(), header.version(),
                header.snapshotId(), header.snapshotVersion(), header.targetPowerKw(),
                header.reserveMarginPercent(), header.requiredPowerKw(), header.plannedPowerKw(),
                header.feasible(), header.weightVersion(), header.createdAt(), candidates
        );
    }

    private record PreviewHeader(
            UUID id, UUID organizationId, UUID vppId, long version,
            UUID snapshotId, long snapshotVersion,
            java.math.BigDecimal targetPowerKw, java.math.BigDecimal reserveMarginPercent,
            java.math.BigDecimal requiredPowerKw, java.math.BigDecimal plannedPowerKw,
            boolean feasible, String weightVersion, Instant createdAt
    ) {
    }
}
