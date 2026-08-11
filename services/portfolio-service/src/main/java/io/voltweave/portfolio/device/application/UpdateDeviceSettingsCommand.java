package io.voltweave.portfolio.device.application;

public record UpdateDeviceSettingsCommand(
        BatteryConfigurationCommand battery,
        EvChargerConfigurationCommand evCharger
) {
}
