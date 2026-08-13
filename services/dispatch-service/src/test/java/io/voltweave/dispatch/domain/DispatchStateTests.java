package io.voltweave.dispatch.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.voltweave.dispatch.domain.entities.DispatchState;
import io.voltweave.dispatch.domain.enums.DispatchStatus;

class DispatchStateTests {
    @Test
    void followsTheScheduledGoldenPath() {
        var state = new DispatchState(DispatchStatus.DRAFT)
                .transitionTo(DispatchStatus.SCHEDULED)
                .transitionTo(DispatchStatus.PREPARING)
                .transitionTo(DispatchStatus.ACTIVE)
                .transitionTo(DispatchStatus.COMPLETING)
                .transitionTo(DispatchStatus.COMPLETED);

        assertEquals(DispatchStatus.COMPLETED, state.status());
    }

    @Test
    void rejectsTransitionsThatBypassWorkflowOwnership() {
        var scheduled = new DispatchState(DispatchStatus.SCHEDULED);

        assertThrows(IllegalStateException.class,
                () -> scheduled.transitionTo(DispatchStatus.ACTIVE));
        assertThrows(IllegalStateException.class,
                () -> new DispatchState(DispatchStatus.COMPLETED)
                        .transitionTo(DispatchStatus.ACTIVE));
    }

    @Test
    void completionCanTakeOwnershipWhileRebalancingAtTheDeadline() {
        var state = new DispatchState(DispatchStatus.REBALANCING)
                .transitionTo(DispatchStatus.COMPLETING)
                .transitionTo(DispatchStatus.PARTIALLY_COMPLETED);

        assertEquals(DispatchStatus.PARTIALLY_COMPLETED, state.status());
    }
}
