package io.voltweave.intelligence.flexibility.application.model;

import java.math.BigDecimal;
import java.util.UUID;

public record FlexibilityCandidate(
        UUID siteId,
        UUID deviceId,
        String deviceType,
        BigDecimal sourcePowerKw,
        BigDecimal rawUpwardFlexibilityKw,
        BigDecimal upwardFlexibilityKw,
        BigDecimal availableEnergyKwh,
        String limitingReason
) {
}
