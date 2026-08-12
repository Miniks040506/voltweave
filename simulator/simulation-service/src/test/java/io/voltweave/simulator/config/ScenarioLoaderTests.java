package io.voltweave.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ScenarioLoaderTests {
    @Test
    void loadsTheFourDeviceExampleScenario() throws Exception {
        var configuration = ScenarioLoader.load(Path.of("scenario.example.json"));

        assertThat(configuration.devices())
                .extracting(DeviceScenario::type)
                .containsExactly(
                        DeviceType.SMART_METER, DeviceType.SOLAR_INVERTER,
                        DeviceType.BATTERY, DeviceType.EV_CHARGER
                );
    }
}
