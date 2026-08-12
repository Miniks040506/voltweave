package io.voltweave.simulator.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.simulator.config.DeviceScenario;
import io.voltweave.simulator.config.DeviceType;
import io.voltweave.simulator.config.MqttCredential;
import io.voltweave.simulator.domain.SimulatedDevice;

class DeviceCommandProcessorTests {
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");

    @Test
    void duplicateCommandReturnsTheOriginalAcknowledgementWithoutApplyingAgain() {
        var processor = processor();
        var command = command(UUID.randomUUID(), null, NOW.plusSeconds(30));

        var first = processor.process(command);
        var duplicate = processor.process(command);

        assertThat(first.status()).isEqualTo("ACCEPTED");
        assertThat(duplicate).isSameAs(first);
    }

    @Test
    void rejectsExpiredAndImplicitlySupersedingCommands() {
        var processor = processor();
        var activeId = UUID.randomUUID();

        assertThat(processor.process(command(UUID.randomUUID(), null, NOW)).status())
                .isEqualTo("REJECTED");
        assertThat(processor.process(command(activeId, null, NOW.plusSeconds(30))).status())
                .isEqualTo("ACCEPTED");
        assertThat(processor.process(command(
                UUID.randomUUID(), null, NOW.plusSeconds(30)
        )).reason()).contains("superseded");
        assertThat(processor.process(command(
                UUID.randomUUID(), activeId, NOW.plusSeconds(30)
        )).status()).isEqualTo("ACCEPTED");
    }

    private static DeviceCommandProcessor processor() {
        var mqtt = new MqttCredential(
                "device", "secret", "device", "root/telemetry", "root/status",
                "root/ack", "root/command"
        );
        var scenario = new DeviceScenario(
                UUID.randomUUID(), DeviceType.BATTERY, mqtt,
                10, 60, 100, 20, 90, 0.9, 42
        );
        return new DeviceCommandProcessor(
                new SimulatedDevice(scenario), Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static DeviceCommand command(UUID id, UUID supersedes, Instant expiresAt) {
        return new DeviceCommand(id, "SET_POWER", -5, expiresAt, supersedes);
    }
}
