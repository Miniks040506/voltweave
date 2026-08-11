package io.voltweave.portfolio.device.api.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.voltweave.portfolio.device.domain.entities.BatteryConfiguration;

public record BatteryConfigurationResponse(
        BigDecimal capacityKwh,
        BigDecimal maxChargeKw,
        BigDecimal maxDischargeKw,
        int minSocPercent,
        int maxSocPercent,
        BigDecimal efficiency,
        Instant updatedAt
) {
    static BatteryConfigurationResponse from(BatteryConfiguration configuration) {
        return configuration == null ? null : new BatteryConfigurationResponse(
                configuration.capacityKwh(), configuration.maxChargeKw(),
                configuration.maxDischargeKw(), configuration.minSocPercent(),
                configuration.maxSocPercent(), configuration.efficiency(),
                configuration.updatedAt()
        );
    }
}
