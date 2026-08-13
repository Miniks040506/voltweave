package io.voltweave.portfolio.device.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.device.domain.enums.DeviceLifecycleStatus;
import io.voltweave.portfolio.device.domain.enums.DeviceType;

public record FlexibilityResource(
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
}
