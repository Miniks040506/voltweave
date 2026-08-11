package io.voltweave.portfolio.device.api.request;

import jakarta.validation.Valid;

public record UpdateDeviceSettingsRequest(
        @Valid BatteryConfigurationRequest battery,
        @Valid EvChargerConfigurationRequest evCharger
) {
}
