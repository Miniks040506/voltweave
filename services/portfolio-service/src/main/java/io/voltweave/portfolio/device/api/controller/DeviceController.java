package io.voltweave.portfolio.device.api.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.device.api.request.BatteryConfigurationRequest;
import io.voltweave.portfolio.device.api.request.CreateDeviceRequest;
import io.voltweave.portfolio.device.api.request.EvChargerConfigurationRequest;
import io.voltweave.portfolio.device.api.request.UpdateDeviceSettingsRequest;
import io.voltweave.portfolio.device.api.response.DeviceProvisioningResponse;
import io.voltweave.portfolio.device.api.response.DeviceResponse;
import io.voltweave.portfolio.device.application.DeviceApplicationService;
import io.voltweave.portfolio.device.application.DeviceProvisioningApplicationService;
import io.voltweave.portfolio.device.application.command.BatteryConfigurationCommand;
import io.voltweave.portfolio.device.application.command.EvChargerConfigurationCommand;
import io.voltweave.portfolio.device.application.command.RegisterDeviceCommand;
import io.voltweave.portfolio.device.application.command.UpdateDeviceSettingsCommand;
import jakarta.validation.Valid;

@RestController
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class DeviceController {
    private final DeviceApplicationService deviceService;
    private final DeviceProvisioningApplicationService provisioningService;

    public DeviceController(
            DeviceApplicationService deviceService,
            DeviceProvisioningApplicationService provisioningService
    ) {
        this.deviceService = deviceService;
        this.provisioningService = provisioningService;
    }

    @PostMapping("/api/v1/devices")
    public ResponseEntity<DeviceResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDeviceRequest request
    ) {
        var profile = deviceService.register(
                new RegisterDeviceCommand(
                        request.siteId(), request.externalDeviceId(), request.type(),
                        request.manufacturer(), request.model(), request.ratedPowerKw(),
                        battery(request.battery()), evCharger(request.evCharger())
                ),
                jwt.getSubject()
        );
        return ResponseEntity.created(URI.create("/api/v1/devices/" + profile.device().id()))
                .body(DeviceResponse.from(profile));
    }

    @GetMapping("/api/v1/devices/{deviceId}")
    public DeviceResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID deviceId
    ) {
        return DeviceResponse.from(deviceService.findForSubject(deviceId, jwt.getSubject()));
    }

    @GetMapping("/api/v1/sites/{siteId}/devices")
    public List<DeviceResponse> findBySite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID siteId
    ) {
        return deviceService.findBySiteForSubject(siteId, jwt.getSubject()).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    @PatchMapping("/api/v1/devices/{deviceId}/settings")
    public DeviceResponse updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID deviceId,
            @Valid @RequestBody UpdateDeviceSettingsRequest request
    ) {
        return DeviceResponse.from(deviceService.updateSettings(
                deviceId,
                new UpdateDeviceSettingsCommand(
                        battery(request.battery()), evCharger(request.evCharger())
                ),
                jwt.getSubject()
        ));
    }

    @PostMapping("/api/v1/devices/{deviceId}/provision")
    public ResponseEntity<DeviceProvisioningResponse> provision(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID deviceId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        var request = provisioningService.provision(
                deviceId, idempotencyKey, jwt.getSubject()
        );
        return ResponseEntity.accepted().body(DeviceProvisioningResponse.from(request));
    }

    private static BatteryConfigurationCommand battery(BatteryConfigurationRequest request) {
        return request == null ? null : new BatteryConfigurationCommand(
                request.capacityKwh(), request.maxChargeKw(), request.maxDischargeKw(),
                request.minSocPercent(), request.maxSocPercent(), request.efficiency()
        );
    }

    private static EvChargerConfigurationCommand evCharger(
            EvChargerConfigurationRequest request
    ) {
        return request == null ? null : new EvChargerConfigurationCommand(
                request.maxChargingKw(), request.vehicleBatteryCapacityKwh(),
                request.targetSocPercent(), request.chargingEfficiency(),
                request.departureAt()
        );
    }
}
