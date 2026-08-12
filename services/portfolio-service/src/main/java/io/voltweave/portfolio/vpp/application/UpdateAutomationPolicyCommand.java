package io.voltweave.portfolio.vpp.application;

import java.math.BigDecimal;
import java.time.Instant;

import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

public record UpdateAutomationPolicyCommand(
        int expectedVersion,
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
        Instant effectiveFrom
) {
}
