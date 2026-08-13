package io.voltweave.dispatch.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
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

    public List<CandidateReference> pendingCandidates(UUID organizationId, UUID vppId) {
        return jdbcClient.sql("""
                SELECT run.policy_id, run.policy_version, run.evaluated_at, run.dispatch_id
                FROM automation_runs run
                JOIN dispatches dispatch
                  ON dispatch.organization_id = run.organization_id
                 AND dispatch.id = run.dispatch_id
                WHERE run.organization_id = :organizationId AND run.vpp_id = :vppId
                  AND run.approval_mode = 'REQUIRE_OPERATOR'
                  AND dispatch.status = 'SCHEDULED'
                  AND dispatch.scheduled_start_at > CURRENT_TIMESTAMP
                ORDER BY run.scheduled_start_at, run.evaluated_at
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .query((row, rowNumber) -> new CandidateReference(
                        row.getObject("policy_id", UUID.class), row.getInt("policy_version"),
                        row.getTimestamp("evaluated_at").toInstant(),
                        row.getObject("dispatch_id", UUID.class)
                )).list();
    }

    public boolean isPendingCandidate(UUID organizationId, UUID dispatchId) {
        return jdbcClient.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM automation_runs run
                    JOIN dispatches dispatch
                      ON dispatch.organization_id = run.organization_id
                     AND dispatch.id = run.dispatch_id
                    WHERE run.organization_id = :organizationId
                      AND run.dispatch_id = :dispatchId
                      AND run.approval_mode = 'REQUIRE_OPERATOR'
                      AND dispatch.status = 'SCHEDULED'
                      AND dispatch.scheduled_start_at > CURRENT_TIMESTAMP
                )
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .query(Boolean.class).single();
    }

    public boolean cancelPendingCandidate(UUID organizationId, UUID dispatchId) {
        return jdbcClient.sql("""
                UPDATE dispatches dispatch
                SET status = 'CANCELLED', version = version + 1
                WHERE dispatch.organization_id = :organizationId
                  AND dispatch.id = :dispatchId AND dispatch.status = 'SCHEDULED'
                  AND EXISTS (
                      SELECT 1 FROM automation_runs run
                      WHERE run.organization_id = dispatch.organization_id
                        AND run.dispatch_id = dispatch.id
                        AND run.approval_mode = 'REQUIRE_OPERATOR'
                  )
                """)
                .param("organizationId", organizationId)
                .param("dispatchId", dispatchId)
                .update() == 1;
    }

    public void expirePendingCandidates(Instant now) {
        jdbcClient.sql("""
                UPDATE dispatches dispatch
                SET status = 'CANCELLED', version = version + 1
                WHERE dispatch.status = 'SCHEDULED'
                  AND dispatch.scheduled_start_at <= :now
                  AND EXISTS (
                      SELECT 1 FROM automation_runs run
                      WHERE run.organization_id = dispatch.organization_id
                        AND run.dispatch_id = dispatch.id
                        AND run.approval_mode = 'REQUIRE_OPERATOR'
                  )
                """)
                .param("now", Timestamp.from(now)).update();
    }

    public record CandidateReference(
            UUID policyId,
            int policyVersion,
            Instant evaluatedAt,
            UUID dispatchId
    ) {
    }
}
