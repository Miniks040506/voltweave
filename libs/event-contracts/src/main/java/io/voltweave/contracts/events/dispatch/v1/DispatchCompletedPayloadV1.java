package io.voltweave.contracts.events.dispatch.v1;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record DispatchCompletedPayloadV1(
        UUID dispatchId,
        UUID vppId,
        String completionStatus,
        BigDecimal targetPowerKw,
        BigDecimal deliveredEnergyKwh,
        UUID baselineId,
        long baselineVersion,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Instant completedAt
) {
    public static final int DELIVERED_ENERGY_SCALE = 6;

    public DispatchCompletedPayloadV1 {
        Objects.requireNonNull(dispatchId, "dispatchId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        if (!"COMPLETED".equals(completionStatus)
                && !"PARTIALLY_COMPLETED".equals(completionStatus)) {
            throw new IllegalArgumentException("completionStatus is invalid");
        }
        if (targetPowerKw == null || targetPowerKw.signum() <= 0) {
            throw new IllegalArgumentException("targetPowerKw must be positive");
        }
        if (deliveredEnergyKwh == null || deliveredEnergyKwh.signum() < 0) {
            throw new IllegalArgumentException("deliveredEnergyKwh cannot be negative");
        }
        deliveredEnergyKwh = deliveredEnergyKwh.setScale(
                DELIVERED_ENERGY_SCALE, RoundingMode.HALF_UP
        );
        Objects.requireNonNull(baselineId, "baselineId is required");
        if (baselineVersion < 1) {
            throw new IllegalArgumentException("baselineVersion must be positive");
        }
        Objects.requireNonNull(scheduledStartAt, "scheduledStartAt is required");
        Objects.requireNonNull(scheduledEndAt, "scheduledEndAt is required");
        Objects.requireNonNull(completedAt, "completedAt is required");
    }
}
