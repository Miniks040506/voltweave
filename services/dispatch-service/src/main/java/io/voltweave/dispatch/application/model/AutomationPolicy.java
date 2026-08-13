package io.voltweave.dispatch.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record AutomationPolicy(
        UUID id,
        UUID organizationId,
        UUID vppId,
        String triggerType,
        String approvalMode,
        BigDecimal peakImportLimitKw,
        BigDecimal priceThreshold,
        int reserveMarginPercent,
        BigDecimal maxDispatchPowerKw,
        int maxDispatchDurationMinutes,
        int version
) {
}
