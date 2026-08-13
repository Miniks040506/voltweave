package io.voltweave.intelligence.optimization.api.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record GenerateOptimizationPreviewRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false)
        BigDecimal targetPowerKw,
        @NotNull @DecimalMin("0") @DecimalMax("100")
        BigDecimal reserveMarginPercent
) {
}
