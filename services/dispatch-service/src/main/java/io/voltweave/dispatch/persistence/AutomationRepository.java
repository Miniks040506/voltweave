package io.voltweave.dispatch.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import io.voltweave.dispatch.application.model.AutomationPlan;
import io.voltweave.dispatch.application.model.AutomationPolicy;

@Repository
public class AutomationRepository {
    private final JdbcClient jdbcClient;

    public AutomationRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void lock(AutomationPolicy policy, Instant scheduledStartAt) {
        jdbcClient.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .param("scope", policy.id() + ":" + policy.version() + ":" + scheduledStartAt)
                .query((row, rowNumber) -> 0).single();
    }

    public Optional<UUID> findDispatch(
            UUID policyId,
            int policyVersion,
            Instant scheduledStartAt
    ) {
        return jdbcClient.sql("""
                SELECT dispatch_id FROM automation_runs
                WHERE policy_id = :policyId AND policy_version = :policyVersion
                  AND scheduled_start_at = :scheduledStartAt
                """)
                .param("policyId", policyId)
                .param("policyVersion", policyVersion)
                .param("scheduledStartAt", Timestamp.from(scheduledStartAt))
                .query(UUID.class).optional();
    }

    public void insert(
            AutomationPolicy policy,
            AutomationPlan plan,
            UUID dispatchId,
            Instant evaluatedAt
    ) {
        jdbcClient.sql("""
                INSERT INTO automation_runs (
                    id, organization_id, vpp_id, policy_id, policy_version,
                    trigger_type, approval_mode, scheduled_start_at,
                    optimization_preview_id, dispatch_id, evaluated_at
                ) VALUES (
                    :id, :organizationId, :vppId, :policyId, :policyVersion,
                    :triggerType, :approvalMode, :scheduledStartAt,
                    :previewId, :dispatchId, :evaluatedAt
                )
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", policy.organizationId())
                .param("vppId", policy.vppId())
                .param("policyId", policy.id())
                .param("policyVersion", policy.version())
                .param("triggerType", policy.triggerType())
                .param("approvalMode", policy.approvalMode())
                .param("scheduledStartAt", Timestamp.from(plan.scheduledStartAt()))
                .param("previewId", plan.optimizationPreviewId())
                .param("dispatchId", dispatchId)
                .param("evaluatedAt", Timestamp.from(evaluatedAt))
                .update();
    }
}
