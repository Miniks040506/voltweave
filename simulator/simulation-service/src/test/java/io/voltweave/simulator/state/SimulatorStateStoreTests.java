package io.voltweave.simulator.state;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.voltweave.simulator.command.DeviceCommand;
import io.voltweave.simulator.command.DeviceCommandProcessor;
import io.voltweave.simulator.config.DeviceScenario;
import io.voltweave.simulator.config.DeviceType;
import io.voltweave.simulator.config.MqttCredential;
import io.voltweave.simulator.domain.SimulatedDevice;
import tools.jackson.databind.json.JsonMapper;

class SimulatorStateStoreTests {
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void restoresSequenceDeviceStateCommandExpiryAndAcknowledgementHistory()
            throws Exception {
        var scenario = scenario();
        var store = new SimulatorStateStore(
                temporaryDirectory.resolve("device.json"),
                JsonMapper.builder().build()
        );
        var device = new SimulatedDevice(scenario);
        var processor = new DeviceCommandProcessor(
                device, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        var command = new DeviceCommand(
                UUID.randomUUID(), "SET_POWER", -5,
                NOW.plus(Duration.ofHours(1)), null
        );
        var acknowledgement = processor.process(command);
        var beforeRestart = device.sample(
                NOW.plus(Duration.ofMinutes(30)), Duration.ofMinutes(30)
        );
        store.save(processor.snapshot());

        var restored = store.load().orElseThrow();
        var restartedDevice = new SimulatedDevice(scenario, restored);
        var restartedProcessor = new DeviceCommandProcessor(
                restartedDevice,
                Clock.fixed(NOW.plus(Duration.ofHours(1)), ZoneOffset.UTC),
                restored
        );

        assertThat(restartedProcessor.expireActiveCommand()).isTrue();
        var afterRestart = restartedDevice.sample(
                NOW.plus(Duration.ofHours(1)), Duration.ZERO
        );
        assertThat(afterRestart.sequenceNumber())
                .isEqualTo(beforeRestart.sequenceNumber() + 1);
        assertThat(afterRestart.socPercent()).isEqualTo(beforeRestart.socPercent());
        assertThat(afterRestart.activePowerKw()).isZero();
        assertThat(restartedProcessor.process(command)).isEqualTo(acknowledgement);
    }

    private static DeviceScenario scenario() {
        var mqtt = new MqttCredential(
                "device", "secret", "device", "root/telemetry", "root/status",
                "root/ack", "root/command"
        );
        return new DeviceScenario(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                DeviceType.BATTERY, mqtt, 10, 60, 100, 20, 90, 0.9, 42
        );
    }
}
