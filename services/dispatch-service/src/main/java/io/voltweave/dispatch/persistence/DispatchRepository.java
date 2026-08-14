package io.voltweave.dispatch.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.domain.enums.DispatchStatus;

@Repository
public class DispatchRepository {
    private static final String CREATE_OPERATION = "POST:/api/v1/dispatches";

    private final JdbcClient jdbcClient;

    public DispatchRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void lockIdempotency(UUID organizationId, String key) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .param("scope", organizationId + ":" + CREATE_OPERATION + ":" + key)
                .query((row, rowNumber) -> 0).single();
    }

    public Optional<IdempotencyRecord> findIdempotency(UUID organizationId, String key) {
        return jdbcClient.sql("""
                SELECT request_hash, resource_id FROM api_idempotency
                WHERE organization_id = :organizationId
                  AND operation = :operation AND idempotency_key = :key
                """)
                .param("organizationId", organizationId)
                .param("operation", CREATE_OPERATION)
                .param("key", key)
                .query((row, rowNumber) -> new IdempotencyRecord(
                        row.getString("request_hash"), row.getObject("resource_id", UUID.class)
                )).optional();
    }

    public void insert(Dispatch dispatch, String idempotencyKey, String requestHash) {
        jdbcClient.sql("""
                INSERT INTO dispatches (
                    id, organization_id, vpp_id, optimization_preview_id,
                    optimization_preview_version, type, target_power_kw, required_power_kw,
                    planned_power_kw, scheduled_start_at, scheduled_end_at, status,
                    created_by, created_at, version
                ) VALUES (
                    :id, :organizationId, :vppId, :previewId,
                    :previewVersion, :type, :targetPowerKw, :requiredPowerKw,
                    :plannedPowerKw, :startAt, :endAt, :status,
                    :createdBy, :createdAt, :version
                )
                """)
                .param("id", dispatch.id())
                .param("organizationId", dispatch.organizationId())
                .param("vppId", dispatch.vppId())
                .param("previewId", dispatch.optimizationPreviewId())
                .param("previewVersion", dispatch.optimizationPreviewVersion())
                .param("type", dispatch.type())
                .param("targetPowerKw", dispatch.targetPowerKw())
                .param("requiredPowerKw", dispatch.requiredPowerKw())
                .param("plannedPowerKw", dispatch.plannedPowerKw())
                .param("startAt", timestamp(dispatch.scheduledStartAt()))
                .param("endAt", timestamp(dispatch.scheduledEndAt()))
                .param("status", dispatch.status().name())
                .param("createdBy", dispatch.createdBy())
                .param("createdAt", timestamp(dispatch.createdAt()))
                .param("version", dispatch.version())
                .update();

        for (var allocation : dispatch.allocations()) {
            jdbcClient.sql("""
                    INSERT INTO dispatch_allocations (
                        organization_id, dispatch_id, site_id, device_id, device_type,
                        source_available_power_kw, allocated_power_kw,
                        expected_energy_kwh, score
                    ) VALUES (
                        :organizationId, :dispatchId, :siteId, :deviceId, :deviceType,
                        :sourcePowerKw, :powerKw, :energyKwh, :score
                    )
                    """)
                    .param("organizationId", dispatch.organizationId())
                    .param("dispatchId", dispatch.id())
                    .param("siteId", allocation.siteId())
                    .param("deviceId", allocation.deviceId())
                    .param("deviceType", allocation.deviceType())
                    .param("sourcePowerKw", allocation.sourceAvailablePowerKw())
                    .param("powerKw", allocation.allocatedPowerKw())
                    .param("energyKwh", allocation.expectedEnergyKwh())
                    .param("score", allocation.score())
                    .update();
        }
        insertBaseline(dispatch);
        jdbcClient.sql("""
                INSERT INTO api_idempotency (
                    organization_id, operation, idempotency_key,
                    request_hash, resource_id, created_at
                ) VALUES (
                    :organizationId, :operation, :key,
                    :requestHash, :resourceId, :createdAt
                )
                """)
                .param("organizationId", dispatch.organizationId())
                .param("operation", CREATE_OPERATION)
                .param("key", idempotencyKey)
                .param("requestHash", requestHash)
                .param("resourceId", dispatch.id())
                .param("createdAt", timestamp(dispatch.createdAt()))
                .update();
    }

    private void insertBaseline(Dispatch dispatch) {
        var baseline = dispatch.baseline();
        jdbcClient.sql("""
                INSERT INTO dispatch_baselines (
                    dispatch_id, organization_id, forecast_id, forecast_version,
                    model_name, model_version, source_valid_until, frozen_at
                ) VALUES (
                    :dispatchId, :organizationId, :forecastId, :forecastVersion,
                    :modelName, :modelVersion, :validUntil, :frozenAt
                )
                """)
                .param("dispatchId", dispatch.id())
                .param("organizationId", dispatch.organizationId())
                .param("forecastId", baseline.forecastId())
                .param("forecastVersion", baseline.forecastVersion())
                .param("modelName", baseline.modelName())
                .param("modelVersion", baseline.modelVersion())
                .param("validUntil", timestamp(baseline.sourceValidUntil()))
                .param("frozenAt", timestamp(baseline.frozenAt()))
                .update();
        for (var point : baseline.points()) {
            jdbcClient.sql("""
                    INSERT INTO dispatch_baseline_points (
                        dispatch_id, forecast_at, baseline_grid_import_kw
                    ) VALUES (:dispatchId, :forecastAt, :powerKw)
                    """)
                    .param("dispatchId", dispatch.id())
                    .param("forecastAt", timestamp(point.forecastAt()))
                    .param("powerKw", point.baselineGridImportKw())
                    .update();
        }
    }

    public Optional<Dispatch> find(UUID organizationId, UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT * FROM dispatches
                WHERE organization_id = :organizationId AND id = :dispatchId
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .query(this::mapHeader).optional().map(this::loadDetails);
    }

    public List<Dispatch> findAll(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT * FROM dispatches
                WHERE organization_id = :organizationId AND vpp_id = :vppId
                ORDER BY created_at DESC, id
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query(this::mapHeader).list().stream()
                .map(this::loadDetails).toList();
    }

    public Optional<Dispatch> findById(UUID dispatchId) {
        return jdbcClient.sql("SELECT * FROM dispatches WHERE id = :dispatchId")
                .param("dispatchId", dispatchId)
                .query(this::mapHeader).optional().map(this::loadDetails);
    }

    private DispatchHeader mapHeader(java.sql.ResultSet row, int rowNumber)
            throws java.sql.SQLException {
        return new DispatchHeader(
                row.getObject("id", UUID.class), row.getObject("organization_id", UUID.class),
                row.getObject("vpp_id", UUID.class),
                row.getObject("optimization_preview_id", UUID.class),
                row.getLong("optimization_preview_version"), row.getString("type"),
                row.getBigDecimal("target_power_kw"), row.getBigDecimal("required_power_kw"),
                row.getBigDecimal("planned_power_kw"),
                row.getTimestamp("scheduled_start_at").toInstant(),
                row.getTimestamp("scheduled_end_at").toInstant(),
                DispatchStatus.valueOf(row.getString("status")), row.getString("created_by"),
                row.getTimestamp("created_at").toInstant(), row.getLong("version")
        );
    }

    private Dispatch loadDetails(DispatchHeader header) {
        List<Dispatch.Allocation> allocations = jdbcClient.sql("""
                SELECT site_id, device_id, device_type, source_available_power_kw,
                       allocated_power_kw, expected_energy_kwh, score
                FROM dispatch_allocations
                WHERE organization_id = :organizationId AND dispatch_id = :dispatchId
                ORDER BY allocated_power_kw DESC, score DESC, device_id
                """)
                .param("organizationId", header.organizationId())
                .param("dispatchId", header.id())
                .query((row, rowNumber) -> new Dispatch.Allocation(
                        row.getObject("site_id", UUID.class), row.getObject("device_id", UUID.class),
                        row.getString("device_type"),
                        row.getBigDecimal("source_available_power_kw"),
                        row.getBigDecimal("allocated_power_kw"),
                        row.getBigDecimal("expected_energy_kwh"), row.getBigDecimal("score")
                )).list();
        var baseline = loadBaseline(header.id());
        return new Dispatch(
                header.id(), header.organizationId(), header.vppId(), header.previewId(),
                header.previewVersion(), header.type(), header.targetPowerKw(),
                header.requiredPowerKw(), header.plannedPowerKw(), header.startAt(), header.endAt(),
                header.status(), header.createdBy(), header.createdAt(), header.version(),
                baseline, allocations
        );
    }

    private Dispatch.Baseline loadBaseline(UUID dispatchId) {
        return jdbcClient.sql("SELECT * FROM dispatch_baselines WHERE dispatch_id = :dispatchId")
                .param("dispatchId", dispatchId)
                .query((row, rowNumber) -> new Dispatch.Baseline(
                        row.getObject("forecast_id", UUID.class), row.getLong("forecast_version"),
                        row.getString("model_name"), row.getString("model_version"),
                        row.getTimestamp("source_valid_until").toInstant(),
                        row.getTimestamp("frozen_at").toInstant(), baselinePoints(dispatchId)
                )).single();
    }

    private List<Dispatch.BaselinePoint> baselinePoints(UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT forecast_at, baseline_grid_import_kw FROM dispatch_baseline_points
                WHERE dispatch_id = :dispatchId ORDER BY forecast_at
                """)
                .param("dispatchId", dispatchId)
                .query((row, rowNumber) -> new Dispatch.BaselinePoint(
                        row.getTimestamp("forecast_at").toInstant(),
                        row.getBigDecimal("baseline_grid_import_kw")
                )).list();
    }

    public record IdempotencyRecord(String requestHash, UUID resourceId) {
    }

    private record DispatchHeader(
            UUID id, UUID organizationId, UUID vppId, UUID previewId, long previewVersion,
            String type, java.math.BigDecimal targetPowerKw,
            java.math.BigDecimal requiredPowerKw, java.math.BigDecimal plannedPowerKw,
            Instant startAt, Instant endAt, DispatchStatus status,
            String createdBy, Instant createdAt, long version
    ) {
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
