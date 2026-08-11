package io.voltweave.portfolio.device.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.device.application.exception.DeviceNotFoundException;
import io.voltweave.portfolio.device.domain.entities.BatteryConfiguration;
import io.voltweave.portfolio.device.domain.entities.Device;
import io.voltweave.portfolio.device.domain.entities.EvChargerConfiguration;
import io.voltweave.portfolio.device.domain.enums.DeviceType;
import io.voltweave.portfolio.device.persistence.BatteryConfigurationRepository;
import io.voltweave.portfolio.device.persistence.DeviceRepository;
import io.voltweave.portfolio.device.persistence.EvChargerConfigurationRepository;
import io.voltweave.portfolio.site.application.SiteApplicationService;

@Service
public class DeviceApplicationService {
    private final SiteApplicationService siteService;
    private final DeviceRepository deviceRepository;
    private final BatteryConfigurationRepository batteryRepository;
    private final EvChargerConfigurationRepository evChargerRepository;

    public DeviceApplicationService(
            SiteApplicationService siteService,
            DeviceRepository deviceRepository,
            BatteryConfigurationRepository batteryRepository,
            EvChargerConfigurationRepository evChargerRepository
    ) {
        this.siteService = siteService;
        this.deviceRepository = deviceRepository;
        this.batteryRepository = batteryRepository;
        this.evChargerRepository = evChargerRepository;
    }

    @Transactional
    public DeviceProfile register(RegisterDeviceCommand command, String subjectId) {
        requireConfiguration(command.type(), command.battery(), command.evCharger());
        var site = siteService.findForSubject(command.siteId(), subjectId).site();
        var now = Instant.now();
        var device = Device.registered(
                site.organizationId(), site.id(), command.externalDeviceId(), command.type(),
                command.manufacturer(), command.model(), command.ratedPowerKw(), now
        );
        deviceRepository.insert(device);

        var battery = battery(device, command.battery(), now);
        var evCharger = evCharger(device, command.evCharger(), now);
        if (battery != null) {
            batteryRepository.insert(battery);
        }
        if (evCharger != null) {
            evChargerRepository.insert(evCharger);
        }
        return new DeviceProfile(device, battery, evCharger);
    }

    @Transactional(readOnly = true)
    public DeviceProfile findForSubject(UUID deviceId, String subjectId) {
        var device = deviceRepository.findByIdForSubject(deviceId, subjectId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        return profile(device);
    }

    @Transactional(readOnly = true)
    public List<DeviceProfile> findBySiteForSubject(UUID siteId, String subjectId) {
        siteService.findForSubject(siteId, subjectId);
        return deviceRepository.findBySiteIdForSubject(siteId, subjectId).stream()
                .map(this::profile)
                .toList();
    }

    @Transactional
    public DeviceProfile updateSettings(
            UUID deviceId,
            UpdateDeviceSettingsCommand command,
            String subjectId
    ) {
        var device = deviceRepository.findByIdForSubject(deviceId, subjectId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        if (device.type() != DeviceType.BATTERY && device.type() != DeviceType.EV_CHARGER) {
            throw new IllegalArgumentException(
                    "Device type " + device.type() + " has no configurable settings"
            );
        }
        requireConfiguration(device.type(), command.battery(), command.evCharger());
        var now = Instant.now();
        var battery = battery(device, command.battery(), now);
        var evCharger = evCharger(device, command.evCharger(), now);
        if (battery != null) {
            batteryRepository.update(battery);
        }
        if (evCharger != null) {
            evChargerRepository.update(evCharger);
        }
        return new DeviceProfile(device, battery, evCharger);
    }

    private DeviceProfile profile(Device device) {
        var battery = device.type() == DeviceType.BATTERY
                ? batteryRepository.findByDeviceId(device.organizationId(), device.id())
                        .orElseThrow(() -> missingConfiguration(device))
                : null;
        var evCharger = device.type() == DeviceType.EV_CHARGER
                ? evChargerRepository.findByDeviceId(device.organizationId(), device.id())
                        .orElseThrow(() -> missingConfiguration(device))
                : null;
        return new DeviceProfile(device, battery, evCharger);
    }

    private static BatteryConfiguration battery(
            Device device,
            BatteryConfigurationCommand command,
            Instant now
    ) {
        return command == null ? null : new BatteryConfiguration(
                device.organizationId(), device.id(), command.capacityKwh(),
                command.maxChargeKw(), command.maxDischargeKw(), command.minSocPercent(),
                command.maxSocPercent(), command.efficiency(), now
        );
    }

    private static EvChargerConfiguration evCharger(
            Device device,
            EvChargerConfigurationCommand command,
            Instant now
    ) {
        if (command == null) {
            return null;
        }
        if (!command.departureAt().isAfter(now)) {
            throw new IllegalArgumentException("departureAt must be in the future");
        }
        return new EvChargerConfiguration(
                device.organizationId(), device.id(), command.maxChargingKw(),
                command.vehicleBatteryCapacityKwh(), command.targetSocPercent(),
                command.chargingEfficiency(), command.departureAt(), now
        );
    }

    private static void requireConfiguration(
            DeviceType type,
            BatteryConfigurationCommand battery,
            EvChargerConfigurationCommand evCharger
    ) {
        boolean valid = switch (type) {
            case BATTERY -> battery != null && evCharger == null;
            case EV_CHARGER -> battery == null && evCharger != null;
            case SMART_METER, SOLAR_INVERTER -> battery == null && evCharger == null;
        };
        if (!valid) {
            throw new IllegalArgumentException("Configuration must match device type " + type);
        }
    }

    private static IllegalStateException missingConfiguration(Device device) {
        return new IllegalStateException("Configuration is missing for device " + device.id());
    }
}
