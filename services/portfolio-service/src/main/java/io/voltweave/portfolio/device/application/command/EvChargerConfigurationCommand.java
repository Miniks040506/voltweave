package io.voltweave.portfolio.device.application.command;

import java.math.BigDecimal;
import java.time.Instant;

public record EvChargerConfigurationCommand(
        BigDecimal maxChargingKw,
        BigDecimal vehicleBatteryCapacityKwh,
        int targetSocPercent,
        BigDecimal chargingEfficiency,
        Instant departureAt
) {
}
