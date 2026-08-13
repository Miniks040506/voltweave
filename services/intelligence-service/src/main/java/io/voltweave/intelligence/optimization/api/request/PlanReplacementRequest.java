package io.voltweave.intelligence.optimization.api.request;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record PlanReplacementRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false)
        BigDecimal missingPowerKw,
        @NotNull @DecimalMin("0") @DecimalMax("100")
        BigDecimal reserveMarginPercent,
        @Min(1) long remainingSeconds,
        @NotEmpty Set<UUID> excludedDeviceIds
) {
}

