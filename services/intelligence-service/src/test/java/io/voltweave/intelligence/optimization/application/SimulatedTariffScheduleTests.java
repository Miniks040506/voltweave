package io.voltweave.intelligence.optimization.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SimulatedTariffScheduleTests {
    @Test
    void returnsConfiguredPriceForUtcPeriod() {
        var tariff = new SimulatedTariffSchedule(
                new BigDecimal("0.10"), new BigDecimal("0.30"), 17, 21
        );

        assertEquals(new BigDecimal("0.10"), tariff.priceAt(
                Instant.parse("2026-08-13T16:59:00Z")
        ));
        assertEquals(new BigDecimal("0.30"), tariff.priceAt(
                Instant.parse("2026-08-13T17:00:00Z")
        ));
        assertEquals(new BigDecimal("0.10"), tariff.priceAt(
                Instant.parse("2026-08-13T21:00:00Z")
        ));
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new SimulatedTariffSchedule(
                BigDecimal.ZERO, BigDecimal.ONE, 21, 17
        ));
    }
}
