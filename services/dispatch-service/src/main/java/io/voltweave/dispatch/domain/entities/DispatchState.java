package io.voltweave.dispatch.domain.entities;

import java.util.EnumSet;
import java.util.Map;

import io.voltweave.dispatch.domain.enums.DispatchStatus;

public record DispatchState(DispatchStatus status) {
    private static final Map<DispatchStatus, EnumSet<DispatchStatus>> TRANSITIONS = Map.of(
            DispatchStatus.DRAFT, EnumSet.of(DispatchStatus.SCHEDULED, DispatchStatus.CANCELLED),
            DispatchStatus.SCHEDULED, EnumSet.of(DispatchStatus.PREPARING, DispatchStatus.CANCELLED),
            DispatchStatus.PREPARING, EnumSet.of(DispatchStatus.ACTIVE, DispatchStatus.FAILED),
            DispatchStatus.ACTIVE, EnumSet.of(
                    DispatchStatus.REBALANCING, DispatchStatus.COMPLETING, DispatchStatus.FAILED
            ),
            DispatchStatus.REBALANCING, EnumSet.of(DispatchStatus.ACTIVE, DispatchStatus.FAILED),
            DispatchStatus.COMPLETING, EnumSet.of(
                    DispatchStatus.COMPLETED, DispatchStatus.PARTIALLY_COMPLETED
            )
    );

    public DispatchState transitionTo(DispatchStatus target) {
        if (!TRANSITIONS.getOrDefault(status, EnumSet.noneOf(DispatchStatus.class))
                .contains(target)) {
            throw new IllegalStateException("Invalid dispatch transition: " + status + " -> " + target);
        }
        return new DispatchState(target);
    }
}
