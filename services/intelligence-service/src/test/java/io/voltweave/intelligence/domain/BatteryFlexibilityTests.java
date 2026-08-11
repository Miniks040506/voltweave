package io.voltweave.intelligence.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BatteryFlexibilityTests {

    @Test
    void limitsDischargeByUsableEnergyForTheRequestedDuration() {
        var battery = availableBattery(80.0, 20.0);

        double availablePowerKw = battery.availablePowerKw(Duration.ofHours(2));

        assertEquals(2.7, availablePowerKw, 0.000_001);
    }

    @Test
    void returnsZeroWhenAnyHardConstraintBlocksDispatch() {
        var offlineBattery = new BatteryFlexibility(
                10.0, 80.0, 20.0, 5.0, 0.9, 4.0,
                false, true, true, true, false);
        var reservedBattery = new BatteryFlexibility(
                10.0, 80.0, 20.0, 5.0, 0.9, 4.0,
                true, true, true, true, true);
        var belowReserveBattery = availableBattery(10.0, 20.0);

        assertEquals(0.0, offlineBattery.availablePowerKw(Duration.ofMinutes(30)));
        assertEquals(0.0, reservedBattery.availablePowerKw(Duration.ofMinutes(30)));
        assertEquals(0.0, belowReserveBattery.availablePowerKw(Duration.ofMinutes(30)));
    }

    @Test
    void rejectsInvalidPhysicalValuesAndDuration() {
        assertThrows(IllegalArgumentException.class, () -> new BatteryFlexibility(
                0.0, 80.0, 20.0, 5.0, 0.9, 4.0,
                true, true, true, true, false));

        var battery = availableBattery(80.0, 20.0);
        assertThrows(IllegalArgumentException.class,
                () -> battery.availablePowerKw(Duration.ZERO));
    }

    private static BatteryFlexibility availableBattery(
            double currentSocPercent,
            double reserveSocPercent) {
        return new BatteryFlexibility(
                10.0,
                currentSocPercent,
                reserveSocPercent,
                5.0,
                0.9,
                4.0,
                true,
                true,
                true,
                true,
                false);
    }
}
