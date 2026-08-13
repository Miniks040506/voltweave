package io.voltweave.intelligence.optimization.api.request;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record PlanAutomationRequest(
        @NotNull UUID organizationId,
        @NotBlank String triggerType,
        @DecimalMin(value = "0", inclusive = false) BigDecimal peakImportLimitKw,
        @DecimalMin(value = "0", inclusive = false) BigDecimal priceThreshold,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxDispatchPowerKw,
        @Min(15) @Max(1440) int maxDispatchDurationMinutes,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal reserveMarginPercent
) {
}
