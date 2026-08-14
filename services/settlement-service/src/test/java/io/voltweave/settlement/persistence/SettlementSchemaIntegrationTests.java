package io.voltweave.settlement.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.settlement.PostgresTestConfiguration;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresTestConfiguration.class)
@Transactional
class SettlementSchemaIntegrationTests {
    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void migratesTenantScopedTablesAndRejectsSettlementMutation() {
        UUID organizationId = UUID.randomUUID();
        UUID settlementId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-13T03:00:00Z");
        jdbcClient.sql("""
                INSERT INTO settlements (
                    id, organization_id, dispatch_id, vpp_id, completion_status,
                    target_power_kw, scheduled_start_at, scheduled_end_at,
                    baseline_frozen_at, baseline_id, baseline_version,
                    baseline_model_name, baseline_model_version,
                    expected_energy_kwh, delivered_energy_kwh,
                    achievement_percent, status, calculated_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :vppId, 'COMPLETED',
                    10, :startAt, :endAt, :frozenAt, :baselineId, 1,
                    'persistence', 'v1', 10, 9.5, 95, 'CALCULATED', :calculatedAt
                )
                """)
                .param("id", settlementId)
                .param("organizationId", organizationId)
                .param("dispatchId", UUID.randomUUID())
                .param("vppId", UUID.randomUUID())
                .param("startAt", Timestamp.from(now.minusSeconds(3_600)))
                .param("endAt", Timestamp.from(now))
                .param("frozenAt", Timestamp.from(now.minusSeconds(7_200)))
                .param("baselineId", UUID.randomUUID())
                .param("calculatedAt", Timestamp.from(now))
                .update();
        jdbcClient.sql("""
                INSERT INTO settlement_lines (
                    organization_id, settlement_id, site_id, participant_id,
                    participant_type, requested_power_kw, expected_energy_kwh,
                    delivered_energy_kwh, achievement_percent
                ) VALUES (
                    :organizationId, :settlementId, :siteId, :participantId,
                    'BATTERY', 10, 10, 9.5, 95
                )
                """)
                .param("organizationId", organizationId)
                .param("settlementId", settlementId)
                .param("siteId", UUID.randomUUID())
                .param("participantId", participantId)
                .update();

        assertThat(jdbcClient.sql("SELECT count(*) FROM settlement_lines")
                .query(Long.class).single()).isEqualTo(1);
        assertThatThrownBy(() -> jdbcClient.sql("""
                UPDATE settlement_lines SET delivered_energy_kwh = 10
                WHERE settlement_id = :settlementId AND participant_id = :participantId
                """)
                .param("settlementId", settlementId)
                .param("participantId", participantId)
                .update()).isInstanceOf(DataAccessException.class)
                .hasMessageContaining("settlement calculation is immutable");
    }

    @Test
    void acceptsOneImmutableBaseRewardPerSettlementParticipant() {
        UUID organizationId = UUID.randomUUID();
        UUID settlementId = insertSettlement(organizationId);
        UUID participantId = UUID.randomUUID();
        insertBaseReward(organizationId, settlementId, participantId);

        assertThat(jdbcClient.sql("SELECT amount FROM reward_ledger_entries")
                .query(java.math.BigDecimal.class).single())
                .isEqualByComparingTo("2.3750");
        assertThatThrownBy(() -> insertBaseReward(
                organizationId, settlementId, participantId
        )).isInstanceOf(DataAccessException.class);
    }

    private UUID insertSettlement(UUID organizationId) {
        UUID settlementId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-13T03:00:00Z");
        jdbcClient.sql("""
                INSERT INTO settlements (
                    id, organization_id, dispatch_id, vpp_id, completion_status,
                    target_power_kw, scheduled_start_at, scheduled_end_at,
                    baseline_frozen_at, baseline_id, baseline_version,
                    baseline_model_name, baseline_model_version,
                    expected_energy_kwh, delivered_energy_kwh,
                    achievement_percent, status, calculated_at
                ) VALUES (:id, :organizationId, :dispatchId, :vppId, 'COMPLETED',
                    10, :startAt, :endAt, :frozenAt, :baselineId, 1,
                    'persistence', 'v1', 10, 9.5, 95, 'CALCULATED', :calculatedAt)
                """)
                .param("id", settlementId)
                .param("organizationId", organizationId)
                .param("dispatchId", UUID.randomUUID())
                .param("vppId", UUID.randomUUID())
                .param("startAt", Timestamp.from(now.minusSeconds(3_600)))
                .param("endAt", Timestamp.from(now))
                .param("frozenAt", Timestamp.from(now.minusSeconds(7_200)))
                .param("baselineId", UUID.randomUUID())
                .param("calculatedAt", Timestamp.from(now))
                .update();
        return settlementId;
    }

    private void insertBaseReward(
            UUID organizationId,
            UUID settlementId,
            UUID participantId
    ) {
        jdbcClient.sql("""
                INSERT INTO reward_ledger_entries (
                    id, organization_id, settlement_id, participant_id,
                    entry_type, energy_kwh, rate_per_kwh, amount, currency,
                    rounding_mode, source_event_id, reason, created_by, created_at
                ) VALUES (:id, :organizationId, :settlementId, :participantId,
                    'BASE_REWARD', 9.5, 0.25, 2.375, 'VWC', 'HALF_UP',
                    :sourceEventId, 'Settlement reward', 'settlement-service', now())
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", organizationId)
                .param("settlementId", settlementId)
                .param("participantId", participantId)
                .param("sourceEventId", UUID.randomUUID())
                .update();
    }
}
