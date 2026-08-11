package io.voltweave.intelligence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EvChargingRequirementTests {

    private static final Instant DEPARTURE = Instant.parse("2026-08-11T18:00:00Z");

    @Test
    void roundsChargingTimeUpToProtectTheDepartureTarget() {
        var requirement = new EvChargingRequirement(50.0, 40.0, 50.0, 7.0, 0.9);

        var latestStart = requirement.latestSafeStart(
                Instant.parse("2026-08-11T16:00:00Z"), DEPARTURE);

        assertEquals(
                Instant.parse("2026-08-11T17:12:22Z"),
                latestStart.orElseThrow());
    }

    @Test
    void reportsWhenTheTargetCannotBeReachedBeforeDeparture() {
        var requirement = new EvChargingRequirement(50.0, 40.0, 50.0, 7.0, 0.9);

        var latestStart = requirement.latestSafeStart(
                Instant.parse("2026-08-11T17:30:00Z"), DEPARTURE);

        assertTrue(latestStart.isEmpty());
    }

    @Test
    void allowsChargingToRemainOffWhenTargetIsAlreadyMet() {
        var requirement = new EvChargingRequirement(50.0, 50.0, 50.0, 7.0, 0.9);

        var latestStart = requirement.latestSafeStart(
                Instant.parse("2026-08-11T17:30:00Z"), DEPARTURE);

        assertEquals(DEPARTURE, latestStart.orElseThrow());
    }

    @Test
    void rejectsAnInvalidSocTargetOrDeparture() {
        assertThrows(IllegalArgumentException.class,
                () -> new EvChargingRequirement(50.0, 60.0, 50.0, 7.0, 0.9));

        var requirement = new EvChargingRequirement(50.0, 40.0, 50.0, 7.0, 0.9);
        assertThrows(IllegalArgumentException.class,
                () -> requirement.latestSafeStart(DEPARTURE, DEPARTURE));
    }
}
