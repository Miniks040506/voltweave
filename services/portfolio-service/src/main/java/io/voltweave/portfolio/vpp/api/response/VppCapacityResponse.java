package io.voltweave.portfolio.vpp.api.response;

import java.math.BigDecimal;
import java.util.UUID;

import io.voltweave.portfolio.vpp.domain.entities.VppInstalledCapacity;

public record VppCapacityResponse(
        UUID vppId,
        long siteCount,
        long deviceCount,
        BigDecimal solarPowerKw,
        BigDecimal batteryPowerKw,
        BigDecimal evChargerPowerKw,
        BigDecimal totalRatedPowerKw
) {
    public static VppCapacityResponse from(
            UUID vppId,
            VppInstalledCapacity capacity
    ) {
        return new VppCapacityResponse(
                vppId, capacity.siteCount(), capacity.deviceCount(),
                capacity.solarPowerKw(), capacity.batteryPowerKw(),
                capacity.evChargerPowerKw(), capacity.totalRatedPowerKw()
        );
    }
}
