package io.voltweave.portfolio.device.domain.entities;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class DeviceConfigurationTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void rejectsAnInvertedBatterySocRange() {
        assertThatThrownBy(() -> new BatteryConfiguration(
                ORGANIZATION_ID, DEVICE_ID, new BigDecimal("13.5"),
                new BigDecimal("5"), new BigDecimal("5"), 90, 20,
                new BigDecimal("0.95"), NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SOC range");
    }

    @Test
    void rejectsAnInvalidEvChargingEfficiency() {
        assertThatThrownBy(() -> new EvChargerConfiguration(
                ORGANIZATION_ID, DEVICE_ID, new BigDecimal("7.4"),
                new BigDecimal("75"), 80, new BigDecimal("1.01"),
                NOW.plusSeconds(3600), NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chargingEfficiency");
    }
}
