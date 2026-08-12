package io.voltweave.portfolio.device.application.model;

import io.voltweave.portfolio.device.domain.entities.BatteryConfiguration;
import io.voltweave.portfolio.device.domain.entities.Device;
import io.voltweave.portfolio.device.domain.entities.EvChargerConfiguration;

public record DeviceProfile(
        Device device,
        BatteryConfiguration battery,
        EvChargerConfiguration evCharger
) {
}
