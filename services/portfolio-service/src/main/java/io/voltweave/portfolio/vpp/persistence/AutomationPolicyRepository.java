package io.voltweave.portfolio.vpp.persistence;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.portfolio.vpp.domain.entities.AutomationPolicy;
import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

@Repository
public class AutomationPolicyRepository {
    private static final RowMapper<AutomationPolicy> ROW_MAPPER =
            (resultSet, rowNumber) -> new AutomationPolicy(
                    resultSet.getObject("id", UUID.class),
                    resultSet.getObject("organization_id", UUID.class),
                    resultSet.getObject("vpp_id", UUID.class),
                    resultSet.getBoolean("enabled"),
                    AutomationTriggerType.valueOf(resultSet.getString("trigger_type")),
                    ApprovalMode.valueOf(resultSet.getString("approval_mode")),
                    resultSet.getBigDecimal("peak_import_limit_kw"),
                    resultSet.getBigDecimal("price_threshold"),
                    resultSet.getInt("reserve_margin_percent"),
                    resultSet.getBigDecimal("max_dispatch_power_kw"),
                    resultSet.getInt("max_dispatch_duration_minutes"),
                    resultSet.getInt("under_delivery_tolerance_percent"),
                    resultSet.getInt("under_delivery_grace_seconds"),
                    resultSet.getInt("rebalance_cooldown_seconds"),
                    resultSet.getTimestamp("effective_from").toInstant(),
                    resultSet.getInt("version"),
                    resultSet.getTimestamp("updated_at").toInstant()
            );

    private final JdbcClient jdbcClient;

    public AutomationPolicyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void insert(AutomationPolicy policy) {
        int rows = jdbcClient.sql("""
                INSERT INTO automation_policies (
                    id, organization_id, vpp_id, enabled, trigger_type, approval_mode,
                    peak_import_limit_kw, price_threshold, reserve_margin_percent,
                    max_dispatch_power_kw, max_dispatch_duration_minutes,
                    under_delivery_tolerance_percent, under_delivery_grace_seconds,
                    rebalance_cooldown_seconds, effective_from, version, updated_at
                ) VALUES (
                    :id, :organizationId, :vppId, :enabled, :triggerType, :approvalMode,
                    :peakImportLimitKw, :priceThreshold, :reserveMarginPercent,
                    :maxDispatchPowerKw, :maxDispatchDurationMinutes,
                    :underDeliveryTolerancePercent, :underDeliveryGraceSeconds,
                    :rebalanceCooldownSeconds, :effectiveFrom, :version, :updatedAt
                )
                """)
                .param("id", policy.id())
                .param("organizationId", policy.organizationId())
                .param("vppId", policy.vppId())
                .param("enabled", policy.enabled())
                .param("triggerType", policy.triggerType().name())
                .param("approvalMode", policy.approvalMode().name())
                .param("peakImportLimitKw", policy.peakImportLimitKw())
                .param("priceThreshold", policy.priceThreshold())
                .param("reserveMarginPercent", policy.reserveMarginPercent())
                .param("maxDispatchPowerKw", policy.maxDispatchPowerKw())
                .param("maxDispatchDurationMinutes", policy.maxDispatchDurationMinutes())
                .param("underDeliveryTolerancePercent", policy.underDeliveryTolerancePercent())
                .param("underDeliveryGraceSeconds", policy.underDeliveryGraceSeconds())
                .param("rebalanceCooldownSeconds", policy.rebalanceCooldownSeconds())
                .param("effectiveFrom", Timestamp.from(policy.effectiveFrom()))
                .param("version", policy.version())
                .param("updatedAt", Timestamp.from(policy.updatedAt()))
                .update();
        if (rows != 1) {
            throw new IllegalStateException("Expected one inserted automation policy");
        }
    }

    public Optional<AutomationPolicy> findCurrent(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT * FROM automation_policies
                WHERE organization_id = :organizationId AND vpp_id = :vppId
                ORDER BY version DESC
                LIMIT 1
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query(ROW_MAPPER)
                .optional();
    }
}
