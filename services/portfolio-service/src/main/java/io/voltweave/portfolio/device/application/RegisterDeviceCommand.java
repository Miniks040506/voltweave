package io.voltweave.portfolio.device.application;

import java.math.BigDecimal;
import java.util.UUID;

import io.voltweave.portfolio.device.domain.enums.DeviceType;

public record RegisterDeviceCommand(
        UUID siteId,
        String externalDeviceId,
        DeviceType type,
        String manufacturer,
        String model,
        BigDecimal ratedPowerKw,
        BatteryConfigurationCommand battery,
        EvChargerConfigurationCommand evCharger
) {
}
