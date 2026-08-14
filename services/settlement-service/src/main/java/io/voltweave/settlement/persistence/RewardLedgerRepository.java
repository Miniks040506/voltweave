package io.voltweave.settlement.persistence;

import java.sql.Timestamp;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.settlement.application.model.RewardLedgerEntry;

@Repository
public class RewardLedgerRepository {
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
}
