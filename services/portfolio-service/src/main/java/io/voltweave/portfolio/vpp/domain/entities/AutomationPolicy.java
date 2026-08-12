package io.voltweave.portfolio.vpp.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

public record AutomationPolicy(
        UUID id,
        UUID organizationId,
        UUID vppId,
        boolean enabled,
        AutomationTriggerType triggerType,
        ApprovalMode approvalMode,
        BigDecimal peakImportLimitKw,
        BigDecimal priceThreshold,
        int reserveMarginPercent,
        BigDecimal maxDispatchPowerKw,
        int maxDispatchDurationMinutes,
        int underDeliveryTolerancePercent,
        int underDeliveryGraceSeconds,
        int rebalanceCooldownSeconds,
        Instant effectiveFrom,
        int version,
        Instant updatedAt
) {
    public AutomationPolicy {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        Objects.requireNonNull(triggerType, "triggerType is required");
        Objects.requireNonNull(approvalMode, "approvalMode is required");
        validateTrigger(triggerType, peakImportLimitKw, priceThreshold);
        requirePercent(reserveMarginPercent, "reserveMarginPercent");
        requirePositive(maxDispatchPowerKw, "maxDispatchPowerKw");
        if (maxDispatchDurationMinutes < 1 || maxDispatchDurationMinutes > 1440) {
            throw new IllegalArgumentException(
                    "maxDispatchDurationMinutes must be between 1 and 1440"
            );
        }
        requirePercent(underDeliveryTolerancePercent, "underDeliveryTolerancePercent");
        if (underDeliveryGraceSeconds < 0 || rebalanceCooldownSeconds < 0) {
            throw new IllegalArgumentException("recovery timings cannot be negative");
        }
        Objects.requireNonNull(effectiveFrom, "effectiveFrom is required");
        if (version < 1) {
            throw new IllegalArgumentException("version must be positive");
        }
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static AutomationPolicy disabledDefaults(VirtualPowerPlant vpp, Instant now) {
        return new AutomationPolicy(
                UUID.randomUUID(), vpp.organizationId(), vpp.id(), false,
                AutomationTriggerType.MANUAL, ApprovalMode.REQUIRE_OPERATOR,
                null, null, 10, BigDecimal.ONE, 15, 10, 30, 60, now, 1, now
        );
    }

    public AutomationPolicy update(
            boolean newEnabled,
            AutomationTriggerType newTriggerType,
            ApprovalMode newApprovalMode,
            BigDecimal newPeakImportLimitKw,
            BigDecimal newPriceThreshold,
            int newReserveMarginPercent,
            BigDecimal newMaxDispatchPowerKw,
            int newMaxDispatchDurationMinutes,
            int newUnderDeliveryTolerancePercent,
            int newUnderDeliveryGraceSeconds,
            int newRebalanceCooldownSeconds,
            Instant newEffectiveFrom,
            Instant now
    ) {
        return new AutomationPolicy(
                id, organizationId, vppId, newEnabled, newTriggerType, newApprovalMode,
                newPeakImportLimitKw, newPriceThreshold, newReserveMarginPercent,
                newMaxDispatchPowerKw, newMaxDispatchDurationMinutes,
                newUnderDeliveryTolerancePercent, newUnderDeliveryGraceSeconds,
                newRebalanceCooldownSeconds, newEffectiveFrom, version + 1, now
        );
    }

    private static void validateTrigger(
            AutomationTriggerType trigger,
            BigDecimal peakLimit,
            BigDecimal price
    ) {
        boolean valid = switch (trigger) {
            case MANUAL -> peakLimit == null && price == null;
            case PEAK_LIMIT -> isPositive(peakLimit) && price == null;
            case PRICE_THRESHOLD -> peakLimit == null && isPositive(price);
        };
        if (!valid) {
            throw new IllegalArgumentException("Threshold must match triggerType " + trigger);
        }
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (!isPositive(value)) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static void requirePercent(int value, String field) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(field + " must be between 0 and 100");
        }
    }
}
