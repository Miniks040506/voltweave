package io.voltweave.portfolio.device.api.request;

import java.math.BigDecimal;
import java.util.UUID;

import io.voltweave.portfolio.device.domain.enums.DeviceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDeviceRequest(
        @NotNull UUID siteId,
        @NotBlank @Size(max = 128) String externalDeviceId,
        @NotNull DeviceType type,
        @NotBlank @Size(max = 100) String manufacturer,
        @NotBlank @Size(max = 100) String model,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal ratedPowerKw,
        @Valid BatteryConfigurationRequest battery,
        @Valid EvChargerConfigurationRequest evCharger
) {
}
