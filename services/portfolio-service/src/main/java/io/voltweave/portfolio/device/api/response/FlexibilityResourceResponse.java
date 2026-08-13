package io.voltweave.portfolio.device.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.device.application.model.FlexibilityResource;
import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.domain.enums.DeviceType;

public record FlexibilityResourceResponse(
        UUID organizationId,
        UUID siteId,
        UUID deviceId,
        DeviceType deviceType,
        DeviceLifecycleStatus status,
        boolean vppOptIn,
        BigDecimal ratedPowerKw,
        BigDecimal capacityKwh,
        BigDecimal maxDischargeKw,
        Integer minimumSocPercent,
        BigDecimal dischargeEfficiency,
        BigDecimal maxChargingKw,
        BigDecimal vehicleBatteryCapacityKwh,
        Integer targetSocPercent,
        BigDecimal chargingEfficiency,
        Instant departureAt
) {
    public static FlexibilityResourceResponse from(FlexibilityResource resource) {
        return new FlexibilityResourceResponse(
                resource.organizationId(), resource.siteId(), resource.deviceId(),
                resource.deviceType(), resource.status(), resource.vppOptIn(),
                resource.ratedPowerKw(), resource.capacityKwh(),
                resource.maxDischargeKw(), resource.minimumSocPercent(),
                resource.dischargeEfficiency(), resource.maxChargingKw(),
                resource.vehicleBatteryCapacityKwh(), resource.targetSocPercent(),
                resource.chargingEfficiency(), resource.departureAt()
        );
    }
}
