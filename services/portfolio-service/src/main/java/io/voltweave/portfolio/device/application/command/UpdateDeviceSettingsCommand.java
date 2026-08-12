package io.voltweave.portfolio.device.application.command;

public record UpdateDeviceSettingsCommand(
        BatteryConfigurationCommand battery,
        EvChargerConfigurationCommand evCharger
) {
}
