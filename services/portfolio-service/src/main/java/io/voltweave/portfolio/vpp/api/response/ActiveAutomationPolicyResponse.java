package io.voltweave.portfolio.vpp.api.response;

import java.math.BigDecimal;
import java.util.UUID;

import io.voltweave.portfolio.vpp.domain.entities.AutomationPolicy;
import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

public record ActiveAutomationPolicyResponse(
        UUID id,
        UUID organizationId,
        UUID vppId,
        AutomationTriggerType triggerType,
        ApprovalMode approvalMode,
        BigDecimal peakImportLimitKw,
        BigDecimal priceThreshold,
        int reserveMarginPercent,
        BigDecimal maxDispatchPowerKw,
        int maxDispatchDurationMinutes,
        int version
) {
    public static ActiveAutomationPolicyResponse from(AutomationPolicy policy) {
        return new ActiveAutomationPolicyResponse(
                policy.id(), policy.organizationId(), policy.vppId(), policy.triggerType(),
                policy.approvalMode(), policy.peakImportLimitKw(), policy.priceThreshold(),
                policy.reserveMarginPercent(), policy.maxDispatchPowerKw(),
                policy.maxDispatchDurationMinutes(), policy.version()
        );
    }
}
