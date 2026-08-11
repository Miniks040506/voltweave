package io.voltweave.portfolio.device.api.request;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EvChargerConfigurationRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal maxChargingKw,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        BigDecimal vehicleBatteryCapacityKwh,
        @Min(1) @Max(100) int targetSocPercent,
        @NotNull @DecimalMin(value = "0", inclusive = false)
        @DecimalMax("1") BigDecimal chargingEfficiency,
        @NotNull @Future Instant departureAt
) {
}
