package io.voltweave.intelligence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.voltweave.intelligence.domain.WeightedAllocator.CandidateResource;
import java.util.List;
import org.junit.jupiter.api.Test;

class WeightedAllocatorTests {

    @Test
    void breaksEqualScoreTiesByStableDeviceId() {
        var deviceB = candidate("device-b", 3.0, true, 1.0, 1.0);
        var deviceA = candidate("device-a", 3.0, true, 1.0, 1.0);

        var firstPlan = WeightedAllocator.allocate(
                List.of(deviceB, deviceA), 4.0, 0.0, WeightedAllocator.Weights.V1);
        var secondPlan = WeightedAllocator.allocate(
                List.of(deviceA, deviceB), 4.0, 0.0, WeightedAllocator.Weights.V1);

        assertEquals(firstPlan.allocations(), secondPlan.allocations());
        assertEquals("device-a", firstPlan.allocations().get(0).deviceId());
        assertEquals(3.0, firstPlan.allocations().get(0).powerKw());
        assertEquals("device-b", firstPlan.allocations().get(1).deviceId());
        assertEquals(1.0, firstPlan.allocations().get(1).powerKw());
    }

    @Test
    void allocatesTheConfiguredReserveMargin() {
        var preferred = candidate("preferred", 8.0, true, 1.0, 1.0);
        var fallback = candidate("fallback", 8.0, true, 0.5, 0.5);

        var plan = WeightedAllocator.allocate(
                List.of(fallback, preferred), 10.0, 10.0, WeightedAllocator.Weights.V1);

        assertTrue(plan.feasible());
        assertEquals(11.0, plan.requiredPowerKw(), 0.000_001);
        assertEquals(11.0, plan.plannedPowerKw(), 0.000_001);
        assertEquals("preferred", plan.allocations().getFirst().deviceId());
    }

    @Test
    void filtersIneligibleCandidatesAndReportsInfeasibility() {
        var optedOut = candidate("opted-out", 10.0, false, 1.0, 1.0);
        var available = candidate("available", 2.0, true, 1.0, 1.0);

        var plan = WeightedAllocator.allocate(
                List.of(optedOut, available), 5.0, 0.0, WeightedAllocator.Weights.V1);

        assertFalse(plan.feasible());
        assertEquals(2.0, plan.plannedPowerKw());
        assertEquals(List.of("available"),
                plan.allocations().stream().map(WeightedAllocator.Allocation::deviceId).toList());
    }

    @Test
    void rejectsDuplicateDevicesAndInvalidWeights() {
        var candidate = candidate("duplicate", 2.0, true, 1.0, 1.0);

        assertThrows(IllegalArgumentException.class, () -> WeightedAllocator.allocate(
                List.of(candidate, candidate), 2.0, 0.0, WeightedAllocator.Weights.V1));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedAllocator.Weights(0.5, 0.5, 0.5, 0.0, 0.0));
    }

    private static CandidateResource candidate(
            String deviceId,
            double availablePowerKw,
            boolean eligible,
            double reliability,
            double availableSoc) {
        return new CandidateResource(
                deviceId,
                availablePowerKw,
                reliability,
                availableSoc,
                0.8,
                0.8,
                0.8,
                eligible);
    }
}
