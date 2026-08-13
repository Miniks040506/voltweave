package io.voltweave.telemetry.ingress;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import io.voltweave.telemetry.command.application.CommandAcknowledgementService;

@Component
@ConditionalOnProperty(
        prefix = "voltweave.ingress", name = "enabled", havingValue = "true"
)
public class MqttTelemetryIngress implements SmartLifecycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(MqttTelemetryIngress.class);
    private static final String CLIENT_ID = "telemetry-service";
    private static final int QOS = 1;

    private final RawTelemetryPublisher publisher;
    private final CommandAcknowledgementService acknowledgementService;
    private final String brokerUri;
    private final String username;
    private final String password;
    private final String topicFilter;
    private final String acknowledgementTopicFilter;
    private volatile MqttClient client;
    private volatile boolean running;

    MqttTelemetryIngress(
            RawTelemetryPublisher publisher,
            CommandAcknowledgementService acknowledgementService,
            @Value("${voltweave.ingress.broker-uri}") String brokerUri,
            @Value("${voltweave.ingress.username}") String username,
            @Value("${voltweave.ingress.password}") String password,
            @Value("${voltweave.ingress.topic}") String topicFilter,
            @Value("${voltweave.ingress.ack-topic}") String acknowledgementTopicFilter
    ) {
        this.publisher = publisher;
        this.acknowledgementService = acknowledgementService;
        this.brokerUri = brokerUri;
        this.username = username;
        this.password = password;
        this.topicFilter = topicFilter;
        this.acknowledgementTopicFilter = acknowledgementTopicFilter;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        try {
            client = new MqttClient(brokerUri, CLIENT_ID, new MemoryPersistence());
            client.setManualAcks(true);
            client.connect(connectOptions());
            client.subscribe(topicFilter, QOS, this::receive);
            client.subscribe(acknowledgementTopicFilter, QOS, this::receive);
            running = true;
            LOGGER.info("Telemetry MQTT ingress subscribed to {}", topicFilter);
        } catch (MqttException exception) {
            closeClient();
            throw new IllegalStateException("Cannot start telemetry MQTT ingress", exception);
        }
    }

    private void receive(String topic, MqttMessage message) throws Exception {
        try {
            if (topic.endsWith("/ack")) {
                acknowledgementService.receive(topic, message.getPayload());
            } else {
                publisher.publish(topic, message);
            }
            client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("Dropping telemetry from invalid MQTT topic {}", topic);
            client.messageArrivedComplete(message.getId(), message.getQos());
        }
    }

    public void publishCommand(String topic, String payload) throws MqttException {
        MqttClient activeClient = client;
        if (!running || activeClient == null || !activeClient.isConnected()) {
            throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
        }
        var message = new MqttMessage(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        message.setQos(QOS);
        activeClient.publish(topic, message);
    }

    private MqttConnectOptions connectOptions() {
        var options = new MqttConnectOptions();
        options.setUserName(username);
        options.setPassword(password.toCharArray());
        options.setAutomaticReconnect(true);
        options.setCleanSession(false);
        return options;
    }

    @Override
    public synchronized void stop() {
        running = false;
        closeClient();
    }

    private void closeClient() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnectForcibly();
            }
            client.close(true);
        } catch (MqttException exception) {
            LOGGER.warn("Cannot close telemetry MQTT client", exception);
        } finally {
            client = null;
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
