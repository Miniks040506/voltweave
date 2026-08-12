package io.voltweave.portfolio.device.application.command;

import java.math.BigDecimal;

public record BatteryConfigurationCommand(
        BigDecimal capacityKwh,
        BigDecimal maxChargeKw,
        BigDecimal maxDischargeKw,
        int minSocPercent,
        int maxSocPercent,
        BigDecimal efficiency
) {
}
