package io.voltweave.settlement.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.settlement.application.model.RewardLedgerEntry;

@Repository
public class RewardLedgerRepository {
    private static final String ADJUST_OPERATION =
            "POST:/api/v1/settlements/{settlementId}/adjustments";

    private final JdbcClient jdbcClient;

    public RewardLedgerRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insertBase(RewardLedgerEntry entry) {
        jdbcClient.sql("""
                INSERT INTO reward_ledger_entries (
                    id, organization_id, settlement_id, participant_id,
                    entry_type, energy_kwh, rate_per_kwh, amount, currency,
                    rounding_mode, source_event_id, reason, created_by, created_at
                ) VALUES (
                    :id, :organizationId, :settlementId, :participantId,
                    'BASE_REWARD', :energyKwh, :ratePerKwh, :amount, :currency,
                    'HALF_UP', :sourceEventId, :reason, :createdBy, :createdAt
                )
                """)
                .param("id", entry.id())
                .param("organizationId", entry.organizationId())
                .param("settlementId", entry.settlementId())
                .param("participantId", entry.participantId())
                .param("energyKwh", entry.energyKwh())
                .param("ratePerKwh", entry.ratePerKwh())
                .param("amount", entry.amount())
                .param("currency", entry.currency())
                .param("sourceEventId", entry.sourceEventId())
                .param("reason", entry.reason())
                .param("createdBy", entry.createdBy())
                .param("createdAt", Timestamp.from(entry.createdAt()))
                .update();
    }

    public List<RewardLedgerEntry> findByParticipantIds(List<UUID> participantIds) {
        if (participantIds.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                SELECT * FROM reward_ledger_entries
                WHERE participant_id IN (:participantIds)
                ORDER BY created_at DESC, id
                """)
                .param("participantIds", participantIds)
                .query((row, rowNumber) -> map(row)).list();
    }

    public List<RewardLedgerEntry> findBySettlementId(UUID settlementId) {
        return jdbcClient.sql("""
                SELECT * FROM reward_ledger_entries
                WHERE settlement_id = :settlementId
                ORDER BY created_at, id
                """)
                .param("settlementId", settlementId)
                .query((row, rowNumber) -> map(row)).list();
    }

    public void lockAdjustmentIdempotency(UUID organizationId, String key) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .param("scope", organizationId + ":" + ADJUST_OPERATION + ":" + key)
                .query((row, rowNumber) -> 0).single();
    }

    public Optional<IdempotencyRecord> findAdjustmentIdempotency(
            UUID organizationId,
            String key
    ) {
        return jdbcClient.sql("""
                SELECT request_hash, resource_id FROM api_idempotency
                WHERE organization_id = :organizationId
                  AND operation = :operation AND idempotency_key = :key
                """)
                .param("organizationId", organizationId)
                .param("operation", ADJUST_OPERATION)
                .param("key", key)
                .query((row, rowNumber) -> new IdempotencyRecord(
                        row.getString("request_hash"), row.getObject("resource_id", UUID.class)
                )).optional();
    }

    public Optional<RewardLedgerEntry> findById(UUID id) {
        return jdbcClient.sql("SELECT * FROM reward_ledger_entries WHERE id = :id")
                .param("id", id).query((row, rowNumber) -> map(row)).optional();
    }

    public void insertAdjustment(RewardLedgerEntry entry, String requestHash) {
        jdbcClient.sql("""
                INSERT INTO reward_ledger_entries (
                    id, organization_id, settlement_id, participant_id,
                    entry_type, amount, currency, rounding_mode, idempotency_key,
                    reason, created_by, created_at
                ) VALUES (
                    :id, :organizationId, :settlementId, :participantId,
                    'ADJUSTMENT', :amount, :currency, 'HALF_UP', :key,
                    :reason, :createdBy, :createdAt
                )
                """)
                .param("id", entry.id())
                .param("organizationId", entry.organizationId())
                .param("settlementId", entry.settlementId())
                .param("participantId", entry.participantId())
                .param("amount", entry.amount())
                .param("currency", entry.currency())
                .param("key", entry.idempotencyKey())
                .param("reason", entry.reason())
                .param("createdBy", entry.createdBy())
                .param("createdAt", Timestamp.from(entry.createdAt()))
                .update();
        jdbcClient.sql("""
                INSERT INTO api_idempotency (
                    organization_id, operation, idempotency_key,
                    request_hash, resource_id, created_at
                ) VALUES (
                    :organizationId, :operation, :key,
                    :requestHash, :resourceId, :createdAt
                )
                """)
                .param("organizationId", entry.organizationId())
                .param("operation", ADJUST_OPERATION)
                .param("key", entry.idempotencyKey())
                .param("requestHash", requestHash)
                .param("resourceId", entry.id())
                .param("createdAt", Timestamp.from(entry.createdAt()))
                .update();
    }

    private static RewardLedgerEntry map(ResultSet row) throws SQLException {
        return new RewardLedgerEntry(
                row.getObject("id", UUID.class),
                row.getObject("organization_id", UUID.class),
                row.getObject("settlement_id", UUID.class),
                row.getObject("participant_id", UUID.class),
                row.getString("entry_type"), row.getBigDecimal("energy_kwh"),
                row.getBigDecimal("rate_per_kwh"), row.getBigDecimal("amount"),
                row.getString("currency"), row.getObject("source_event_id", UUID.class),
                row.getString("idempotency_key"), row.getString("reason"),
                row.getString("created_by"), row.getTimestamp("created_at").toInstant()
        );
    }

    public record IdempotencyRecord(String requestHash, UUID resourceId) {
    }
}
