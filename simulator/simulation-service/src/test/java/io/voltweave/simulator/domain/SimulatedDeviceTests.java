package io.voltweave.simulator.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.simulator.config.DeviceScenario;
import io.voltweave.simulator.config.DeviceType;
import io.voltweave.simulator.config.MqttCredential;

class SimulatedDeviceTests {
    private static final Instant NOON = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void loadAndSolarProfilesAreDeterministicAndUseGridSignConvention() {
        var firstMeter = new SimulatedDevice(scenario(DeviceType.SMART_METER));
        var secondMeter = new SimulatedDevice(scenario(DeviceType.SMART_METER));
        var solar = new SimulatedDevice(scenario(DeviceType.SOLAR_INVERTER));

        var first = firstMeter.sample(NOON, Duration.ZERO);
        var repeated = secondMeter.sample(NOON, Duration.ZERO);

        assertThat(first.activePowerKw()).isPositive().isEqualTo(repeated.activePowerKw());
        assertThat(solar.sample(NOON, Duration.ZERO).activePowerKw()).isNegative();
        assertThat(first.sequenceNumber()).isEqualTo(1);
    }

    @Test
    void batteryDischargeReducesSocAndRejectsPowerAboveRating() {
        var battery = new SimulatedDevice(scenario(DeviceType.BATTERY));

        assertThat(battery.setPower(-5).accepted()).isTrue();
        var telemetry = battery.sample(NOON, Duration.ofHours(1));

        assertThat(telemetry.activePowerKw()).isEqualTo(-5);
        assertThat(telemetry.socPercent()).isLessThan(60);
        assertThat(battery.setPower(-11).accepted()).isFalse();
    }

    @Test
    void evChargerNeverExportsPower() {
        var charger = new SimulatedDevice(scenario(DeviceType.EV_CHARGER));

        assertThat(charger.setPower(-1).accepted()).isFalse();
        assertThat(charger.setPower(7).accepted()).isTrue();
        assertThat(charger.sample(NOON, Duration.ofMinutes(30)).socPercent()).isGreaterThan(60);
    }

    private static DeviceScenario scenario(DeviceType type) {
        var credential = new MqttCredential(
                "device", "secret", "device", "root/telemetry", "root/status",
                "root/ack", "root/command"
        );
        return new DeviceScenario(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                type, credential, 10, 60, 100, 20, 90, 0.9, 42
        );
    }
}
