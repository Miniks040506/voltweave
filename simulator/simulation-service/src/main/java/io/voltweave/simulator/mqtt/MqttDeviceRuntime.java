package io.voltweave.simulator.mqtt;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import io.voltweave.simulator.command.DeviceCommand;
import io.voltweave.simulator.command.DeviceCommandProcessor;
import io.voltweave.simulator.config.DeviceScenario;
import io.voltweave.simulator.domain.SimulatedDevice;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class MqttDeviceRuntime implements AutoCloseable {
    private static final int QOS = 1;

    private final DeviceScenario scenario;
    private final SimulatedDevice device;
    private final DeviceCommandProcessor commandProcessor;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration interval;
    private final MqttClient client;
    private final ScheduledExecutorService scheduler;

    public MqttDeviceRuntime(
            String brokerUri,
            DeviceScenario scenario,
            int intervalSeconds,
            ObjectMapper objectMapper,
            Clock clock
    ) throws MqttException {
        this.scenario = scenario;
        this.device = new SimulatedDevice(scenario);
        this.commandProcessor = new DeviceCommandProcessor(device, clock);
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.interval = Duration.ofSeconds(intervalSeconds);
        this.client = new MqttClient(
                brokerUri, scenario.mqtt().clientId(), new MemoryPersistence()
        );
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "simulator-" + scenario.deviceId());
            thread.setDaemon(false);
            return thread;
        });
    }

    public void start() throws MqttException {
        var options = new MqttConnectOptions();
        options.setUserName(scenario.mqtt().username());
        options.setPassword(scenario.mqtt().password().toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        client.connect(options);
        client.subscribe(scenario.mqtt().commandTopic(), QOS, this::handleCommand);
        publishStatus("ONLINE");
        scheduler.scheduleWithFixedDelay(
                this::publishTelemetrySafely, 0, interval.toSeconds(), TimeUnit.SECONDS
        );
    }

    private void handleCommand(String topic, MqttMessage message) {
        try {
            var command = objectMapper.readValue(message.getPayload(), DeviceCommand.class);
            var acknowledgement = commandProcessor.process(command);
            publish(scenario.mqtt().acknowledgementTopic(), acknowledgement, false);
        } catch (JacksonException exception) {
            System.err.printf("Invalid command for device %s: %s%n",
                    scenario.deviceId(), exception.getMessage());
        } catch (MqttException exception) {
            System.err.printf("Cannot acknowledge command for device %s: %s%n",
                    scenario.deviceId(), exception.getMessage());
        }
    }

    private void publishTelemetrySafely() {
        if (!client.isConnected()) {
            return;
        }
        try {
            publish(
                    scenario.mqtt().telemetryTopic(),
                    device.sample(clock.instant(), interval),
                    false
            );
        } catch (MqttException | JacksonException exception) {
            System.err.printf("Cannot publish telemetry for device %s: %s%n",
                    scenario.deviceId(), exception.getMessage());
        }
    }

    private void publishStatus(String status) throws MqttException {
        try {
            publish(
                    scenario.mqtt().statusTopic(),
                    new DeviceStatus(scenario.deviceId(), status, clock.instant()),
                    true
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize device status", exception);
        }
    }

    private void publish(String topic, Object payload, boolean retained)
            throws MqttException, JacksonException {
        var message = new MqttMessage(objectMapper.writeValueAsBytes(payload));
        message.setQos(QOS);
        message.setRetained(retained);
        client.publish(topic, message);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        try {
            if (client.isConnected()) {
                publishStatus("OFFLINE");
                client.disconnectForcibly();
            }
            client.close(true);
        } catch (MqttException exception) {
            System.err.printf("Cannot close device %s: %s%n",
                    scenario.deviceId(), exception.getMessage());
        }
    }
}
