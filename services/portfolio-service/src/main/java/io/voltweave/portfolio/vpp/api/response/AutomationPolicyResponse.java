package io.voltweave.portfolio.vpp.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.vpp.domain.entities.AutomationPolicy;
import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

public record AutomationPolicyResponse(
        UUID id,
        boolean enabled,
        AutomationTriggerType triggerType,
        ApprovalMode approvalMode,
        BigDecimal peakImportLimitKw,
        BigDecimal priceThreshold,
        int reserveMarginPercent,
        BigDecimal maxDispatchPowerKw,
        int maxDispatchDurationMinutes,
        int underDeliveryTolerancePercent,
        int underDeliveryGraceSeconds,
        int rebalanceCooldownSeconds,
        Instant effectiveFrom,
        int version,
        Instant updatedAt
) {
    public static AutomationPolicyResponse from(AutomationPolicy policy) {
        return new AutomationPolicyResponse(
                policy.id(), policy.enabled(), policy.triggerType(), policy.approvalMode(),
                policy.peakImportLimitKw(), policy.priceThreshold(),
                policy.reserveMarginPercent(), policy.maxDispatchPowerKw(),
                policy.maxDispatchDurationMinutes(),
                policy.underDeliveryTolerancePercent(),
                policy.underDeliveryGraceSeconds(), policy.rebalanceCooldownSeconds(),
                policy.effectiveFrom(), policy.version(), policy.updatedAt()
        );
    }
}
