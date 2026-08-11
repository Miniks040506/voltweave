package io.voltweave.portfolio.device.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.device.application.DeviceProfile;
import io.voltweave.portfolio.device.domain.enums.CommunicationProtocol;
import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.domain.enums.DeviceType;

public record DeviceResponse(
        UUID id,
        UUID organizationId,
        UUID siteId,
        String externalDeviceId,
        DeviceType type,
        String manufacturer,
        String model,
        BigDecimal ratedPowerKw,
        DeviceLifecycleStatus status,
        CommunicationProtocol communicationProtocol,
        BatteryConfigurationResponse battery,
        EvChargerConfigurationResponse evCharger,
        Instant createdAt,
        Instant updatedAt
) {
    public static DeviceResponse from(DeviceProfile profile) {
        var device = profile.device();
        return new DeviceResponse(
                device.id(), device.organizationId(), device.siteId(),
                device.externalDeviceId(), device.type(), device.manufacturer(),
                device.model(), device.ratedPowerKw(), device.status(),
                device.communicationProtocol(),
                BatteryConfigurationResponse.from(profile.battery()),
                EvChargerConfigurationResponse.from(profile.evCharger()),
                device.createdAt(), device.updatedAt()
        );
    }
}
