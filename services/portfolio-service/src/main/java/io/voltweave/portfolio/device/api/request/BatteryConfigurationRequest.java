package io.voltweave.portfolio.device.api.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BatteryConfigurationRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal capacityKwh,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxChargeKw,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxDischargeKw,
        @Min(0) @Max(100) int minSocPercent,
        @Min(0) @Max(100) int maxSocPercent,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("1") BigDecimal efficiency
) {
}
