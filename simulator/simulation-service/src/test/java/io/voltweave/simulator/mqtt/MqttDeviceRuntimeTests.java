package io.voltweave.simulator.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import io.voltweave.simulator.command.CommandAcknowledgement;
import io.voltweave.simulator.command.DeviceCommand;
import io.voltweave.simulator.config.DeviceScenario;
import io.voltweave.simulator.config.DeviceType;
import io.voltweave.simulator.config.MqttCredential;
import io.voltweave.simulator.domain.DeviceTelemetry;
import tools.jackson.databind.json.JsonMapper;

class MqttDeviceRuntimeTests {
    @TempDir
    Path stateDirectory;

    private static final GenericContainer<?> MOSQUITTO =
            new GenericContainer<>("eclipse-mosquitto:2.0.22")
                    .withExposedPorts(1883)
                    .withCopyToContainer(
                            MountableFile.forClasspathResource("mosquitto/mosquitto.conf"),
                            "/opt/mosquitto.conf"
                    )
                    .withCopyToContainer(
                            MountableFile.forClasspathResource("mosquitto/entrypoint.sh", 0744),
                            "/opt/entrypoint.sh"
                    )
                    .withCreateContainerCmdModifier(command -> command
                            .withEntrypoint("/bin/sh")
                            .withCmd("/opt/entrypoint.sh"))
                    .waitingFor(Wait.forLogMessage(".*mosquitto version .* running.*", 1));

    @BeforeAll
    static void startBroker() {
        MOSQUITTO.start();
    }

    @AfterAll
    static void stopBroker() {
        MOSQUITTO.stop();
    }

    @Test
    void publishesSequencedTelemetryAndReplaysDuplicateAcknowledgement() throws Exception {
        var mapper = JsonMapper.builder().build();
        var telemetry = new LinkedBlockingQueue<DeviceTelemetry>();
        var acknowledgements = new LinkedBlockingQueue<CommandAcknowledgement>();
        String broker = "tcp://localhost:" + MOSQUITTO.getMappedPort(1883);
        var observer = connect(broker, "observer", "observer-secret", "observer-client");
        observer.subscribe("root/telemetry", 1, (topic, message) -> telemetry.add(
                mapper.readValue(message.getPayload(), DeviceTelemetry.class)
        ));
        observer.subscribe("root/ack", 1, (topic, message) -> acknowledgements.add(
                mapper.readValue(message.getPayload(), CommandAcknowledgement.class)
        ));

        var runtime = new MqttDeviceRuntime(
                broker, scenario(), 1, mapper, Clock.systemUTC(), stateDirectory
        );
        try {
            runtime.start();
            var first = telemetry.poll(5, TimeUnit.SECONDS);
            var second = telemetry.poll(5, TimeUnit.SECONDS);
            assertThat(first).isNotNull();
            assertThat(second.sequenceNumber()).isEqualTo(first.sequenceNumber() + 1);

            var command = new DeviceCommand(
                    UUID.randomUUID(), "SET_POWER", -4,
                    Instant.now().plusSeconds(30), null
            );
            publish(observer, mapper.writeValueAsBytes(command));
            var accepted = acknowledgements.poll(5, TimeUnit.SECONDS);
            publish(observer, mapper.writeValueAsBytes(command));
            var duplicate = acknowledgements.poll(5, TimeUnit.SECONDS);

            assertThat(accepted.status()).isEqualTo("ACCEPTED");
            assertThat(duplicate).isEqualTo(accepted);
        } finally {
            runtime.close();
            observer.disconnectForcibly();
            observer.close(true);
        }
    }

    private static DeviceScenario scenario() {
        var mqtt = new MqttCredential(
                "device", "device-secret", "device-client", "root/telemetry",
                "root/status", "root/ack", "root/command"
        );
        return new DeviceScenario(
                UUID.randomUUID(), DeviceType.BATTERY, mqtt,
                10, 60, 100, 20, 90, 0.9, 42
        );
    }

    private static MqttClient connect(
            String broker,
            String username,
            String password,
            String clientId
    ) throws Exception {
        var client = new MqttClient(broker, clientId, new MemoryPersistence());
        var options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        client.connect(options);
        return client;
    }

    private static void publish(MqttClient client, byte[] payload) throws Exception {
        var message = new MqttMessage(payload);
        message.setQos(1);
        client.publish("root/command", message);
    }
}
