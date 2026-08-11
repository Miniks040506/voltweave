package io.voltweave.portfolio.device.api.response;

import java.math.BigDecimal;
import java.time.Instant;

import io.voltweave.portfolio.device.domain.entities.EvChargerConfiguration;

public record EvChargerConfigurationResponse(
        BigDecimal maxChargingKw,
        BigDecimal vehicleBatteryCapacityKwh,
        int targetSocPercent,
        BigDecimal chargingEfficiency,
        Instant departureAt,
        Instant updatedAt
) {
    static EvChargerConfigurationResponse from(EvChargerConfiguration configuration) {
        return configuration == null ? null : new EvChargerConfigurationResponse(
                configuration.maxChargingKw(), configuration.vehicleBatteryCapacityKwh(),
                configuration.targetSocPercent(), configuration.chargingEfficiency(),
                configuration.departureAt(), configuration.updatedAt()
        );
    }
}
