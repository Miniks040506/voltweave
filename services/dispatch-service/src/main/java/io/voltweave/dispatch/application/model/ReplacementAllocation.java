package io.voltweave.dispatch.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ReplacementAllocation(
        UUID siteId,
        UUID deviceId,
        String deviceType,
        BigDecimal sourceAvailablePowerKw,
        BigDecimal allocatedPowerKw,
        BigDecimal expectedEnergyKwh,
        BigDecimal score
) {
}

