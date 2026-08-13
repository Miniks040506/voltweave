package io.voltweave.settlement.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.settlement.application.model.Settlement;

@Repository
public class SettlementRepository {
    public static final String DISPATCH_CONSUMER = "settlement-dispatch-v1";

    private final JdbcClient jdbcClient;

    public SettlementRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean recordEventIfNew(UUID eventId, String eventType, Instant receivedAt) {
        return jdbcClient.sql("""
                INSERT INTO event_inbox (consumer_name, event_id, event_type, received_at)
                VALUES (:consumer, :eventId, :eventType, :receivedAt)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """)
                .param("consumer", DISPATCH_CONSUMER)
                .param("eventId", eventId)
                .param("eventType", eventType)
                .param("receivedAt", Timestamp.from(receivedAt))
                .update() == 1;
    }

    public void insert(Settlement settlement) {
        jdbcClient.sql("""
                INSERT INTO settlements (
                    id, organization_id, dispatch_id, vpp_id, completion_status,
                    target_power_kw, scheduled_start_at, scheduled_end_at,
                    baseline_frozen_at, baseline_id, baseline_version,
                    baseline_model_name, baseline_model_version,
                    expected_energy_kwh, delivered_energy_kwh,
                    achievement_percent, status, calculated_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :vppId, :completionStatus,
                    :targetPowerKw, :startAt, :endAt,
                    :baselineFrozenAt, :baselineId, :baselineVersion,
                    :baselineModelName, :baselineModelVersion,
                    :expectedEnergyKwh, :deliveredEnergyKwh,
                    :achievementPercent, 'CALCULATED', :calculatedAt
                )
                """)
                .param("id", settlement.id())
                .param("organizationId", settlement.organizationId())
                .param("dispatchId", settlement.dispatchId())
                .param("vppId", settlement.vppId())
                .param("completionStatus", settlement.completionStatus())
                .param("targetPowerKw", settlement.targetPowerKw())
                .param("startAt", timestamp(settlement.scheduledStartAt()))
                .param("endAt", timestamp(settlement.scheduledEndAt()))
                .param("baselineFrozenAt", timestamp(settlement.baselineFrozenAt()))
                .param("baselineId", settlement.baselineId())
                .param("baselineVersion", settlement.baselineVersion())
                .param("baselineModelName", settlement.baselineModelName())
                .param("baselineModelVersion", settlement.baselineModelVersion())
                .param("expectedEnergyKwh", settlement.expectedEnergyKwh())
                .param("deliveredEnergyKwh", settlement.deliveredEnergyKwh())
                .param("achievementPercent", settlement.achievementPercent())
                .param("calculatedAt", timestamp(settlement.calculatedAt()))
                .update();

        for (var point : settlement.baselinePoints()) {
            jdbcClient.sql("""
                    INSERT INTO settlement_baseline_points (
                        organization_id, settlement_id, forecast_at, baseline_grid_import_kw
                    ) VALUES (:organizationId, :settlementId, :forecastAt, :powerKw)
                    """)
                    .param("organizationId", settlement.organizationId())
                    .param("settlementId", settlement.id())
                    .param("forecastAt", timestamp(point.forecastAt()))
                    .param("powerKw", point.baselineGridImportKw())
                    .update();
        }
        for (var line : settlement.lines()) {
            jdbcClient.sql("""
                    INSERT INTO settlement_lines (
                        organization_id, settlement_id, site_id, participant_id,
                        participant_type, requested_power_kw, expected_energy_kwh,
                        delivered_energy_kwh, achievement_percent
                    ) VALUES (
                        :organizationId, :settlementId, :siteId, :participantId,
                        :participantType, :requestedPowerKw, :expectedEnergyKwh,
                        :deliveredEnergyKwh, :achievementPercent
                    )
                    """)
                    .param("organizationId", settlement.organizationId())
                    .param("settlementId", settlement.id())
                    .param("siteId", line.siteId())
                    .param("participantId", line.participantId())
                    .param("participantType", line.participantType())
                    .param("requestedPowerKw", line.requestedPowerKw())
                    .param("expectedEnergyKwh", line.expectedEnergyKwh())
                    .param("deliveredEnergyKwh", line.deliveredEnergyKwh())
                    .param("achievementPercent", line.achievementPercent())
                    .update();
        }
    }

    public Optional<Settlement> findById(UUID settlementId) {
        return find("id", settlementId);
    }

    public Optional<Settlement> findByDispatchId(UUID dispatchId) {
        return find("dispatch_id", dispatchId);
    }

    private Optional<Settlement> find(String column, UUID value) {
        return jdbcClient.sql("""
                SELECT * FROM settlements WHERE %s = :value
                """.formatted(column))
                .param("value", value)
                .query((row, rowNumber) -> new Settlement(
                        row.getObject("id", UUID.class),
                        row.getObject("organization_id", UUID.class),
                        row.getObject("dispatch_id", UUID.class),
                        row.getObject("vpp_id", UUID.class),
                        row.getString("completion_status"),
                        row.getBigDecimal("target_power_kw"),
                        row.getTimestamp("scheduled_start_at").toInstant(),
                        row.getTimestamp("scheduled_end_at").toInstant(),
                        row.getTimestamp("baseline_frozen_at").toInstant(),
                        row.getObject("baseline_id", UUID.class),
                        row.getLong("baseline_version"),
                        row.getString("baseline_model_name"),
                        row.getString("baseline_model_version"),
                        row.getBigDecimal("expected_energy_kwh"),
                        row.getBigDecimal("delivered_energy_kwh"),
                        row.getBigDecimal("achievement_percent"),
                        row.getString("status"),
                        row.getTimestamp("calculated_at").toInstant(),
                        baselinePoints(row.getObject("id", UUID.class)),
                        lines(row.getObject("id", UUID.class))
                )).optional();
    }

    private List<Settlement.BaselinePoint> baselinePoints(UUID settlementId) {
        return jdbcClient.sql("""
                SELECT forecast_at, baseline_grid_import_kw
                FROM settlement_baseline_points
                WHERE settlement_id = :settlementId ORDER BY forecast_at
                """)
                .param("settlementId", settlementId)
                .query((row, rowNumber) -> new Settlement.BaselinePoint(
                        row.getTimestamp("forecast_at").toInstant(),
                        row.getBigDecimal("baseline_grid_import_kw")
                )).list();
    }

    private List<Settlement.Line> lines(UUID settlementId) {
        return jdbcClient.sql("""
                SELECT site_id, participant_id, participant_type,
                       requested_power_kw, expected_energy_kwh,
                       delivered_energy_kwh, achievement_percent
                FROM settlement_lines
                WHERE settlement_id = :settlementId ORDER BY site_id, participant_id
                """)
                .param("settlementId", settlementId)
                .query((row, rowNumber) -> new Settlement.Line(
                        row.getObject("site_id", UUID.class),
                        row.getObject("participant_id", UUID.class),
                        row.getString("participant_type"),
                        row.getBigDecimal("requested_power_kw"),
                        row.getBigDecimal("expected_energy_kwh"),
                        row.getBigDecimal("delivered_energy_kwh"),
                        row.getBigDecimal("achievement_percent")
                )).list();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
