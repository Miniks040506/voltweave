package io.voltweave.portfolio.device.application;

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
