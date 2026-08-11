package io.voltweave.settlement.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.voltweave.settlement.domain.DeliveredEnergyIntegrator.Interval;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeliveredEnergyIntegratorTests {

    @Test
    void integratesOnlyPositiveReductionAgainstTheBaseline() {
        var intervals = List.of(
                new Interval(5.0, 3.0, Duration.ofMinutes(15)),
                new Interval(3.0, 4.0, Duration.ofMinutes(15)),
                new Interval(-1.0, -2.0, Duration.ofMinutes(30)));

        double deliveredEnergyKwh = DeliveredEnergyIntegrator.integrateKwh(intervals);

        assertEquals(1.0, deliveredEnergyKwh, 0.000_001);
    }

    @Test
    void returnsZeroWhenThereAreNoPerformanceIntervals() {
        assertEquals(0.0, DeliveredEnergyIntegrator.integrateKwh(List.of()));
    }

    @Test
    void rejectsInvalidMeasurementsAndDurations() {
        assertThrows(IllegalArgumentException.class,
                () -> new Interval(Double.NaN, 2.0, Duration.ofMinutes(5)));
        assertThrows(IllegalArgumentException.class,
                () -> new Interval(3.0, 2.0, Duration.ZERO));
    }
}
