package io.voltweave.portfolio.vpp.api.request;

import java.math.BigDecimal;
import java.time.Instant;

import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateAutomationPolicyRequest(
        @NotNull @Min(1) Integer expectedVersion,
        @NotNull Boolean enabled,
        @NotNull AutomationTriggerType triggerType,
        @NotNull ApprovalMode approvalMode,
        @DecimalMin(value = "0", inclusive = false) BigDecimal peakImportLimitKw,
        @DecimalMin(value = "0", inclusive = false) BigDecimal priceThreshold,
        @NotNull @Min(0) @Max(100) Integer reserveMarginPercent,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        BigDecimal maxDispatchPowerKw,
        @NotNull @Min(1) @Max(1440) Integer maxDispatchDurationMinutes,
        @NotNull @Min(0) @Max(100) Integer underDeliveryTolerancePercent,
        @NotNull @Min(0) Integer underDeliveryGraceSeconds,
        @NotNull @Min(0) Integer rebalanceCooldownSeconds,
        @NotNull Instant effectiveFrom
) {
}
