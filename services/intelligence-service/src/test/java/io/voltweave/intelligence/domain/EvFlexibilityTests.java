package io.voltweave.intelligence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class EvFlexibilityTests {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Instant DEPARTURE = Instant.parse("2026-08-13T04:00:00Z");
    private static final Duration HOUR = Duration.ofHours(1);

    @Test
    void curtailsCurrentChargingWhenTheTargetRemainsReachable() {
        var flexibility = availableEv(7.0);

        assertEquals(7.0, flexibility.curtailablePowerKw(NOW, DEPARTURE, HOUR));
    }

    @Test
    void protectsTheDepartureTargetAndHardConstraints() {
        var requirement = new EvChargingRequirement(60.0, 40.0, 80.0, 7.0, 0.9);
        var constrained = new EvFlexibility(
                requirement, 7.0, false, true, true, false
        );

        assertEquals(0.0, constrained.curtailablePowerKw(NOW, DEPARTURE, HOUR));
        var departureConstrained = new EvFlexibility(
                requirement, 7.0, true, true, true, false
        );
        assertEquals(0.0, departureConstrained.curtailablePowerKw(
                Instant.parse("2026-08-13T03:00:00Z"), DEPARTURE, HOUR
        ));
    }

    @Test
    void capsCurtailmentAndRejectsInvalidInputs() {
        assertEquals(7.0, availableEv(10.0)
                .curtailablePowerKw(NOW, DEPARTURE, HOUR));
        assertThrows(IllegalArgumentException.class, () -> new EvFlexibility(
                new EvChargingRequirement(60.0, 80.0, 80.0, 7.0, 0.9),
                -1.0, true, true, true, false
        ));
    }

    private static EvFlexibility availableEv(double chargingPowerKw) {
        return new EvFlexibility(
                new EvChargingRequirement(60.0, 80.0, 80.0, 7.0, 0.9),
                chargingPowerKw, true, true, true, false
        );
    }
}
