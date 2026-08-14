package io.voltweave.portfolio.device.mqtt;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.voltweave.portfolio.device.application.MqttBrokerAdmin;
import io.voltweave.portfolio.device.application.model.MqttDeviceCredential;
import io.voltweave.portfolio.device.domain.entities.Device;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class MosquittoDynamicSecurityClient implements MqttBrokerAdmin {
    private static final String CONTROL_TOPIC = "$CONTROL/dynamic-security/v1";
    private static final String RESPONSE_TOPIC = CONTROL_TOPIC + "/response";
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final String brokerUri;
    private final String username;
    private final String password;
    private final SecureRandom secureRandom = new SecureRandom();

    public MosquittoDynamicSecurityClient(
            ObjectMapper objectMapper,
            @Value("${voltweave.mqtt.broker-uri}") String brokerUri,
            @Value("${voltweave.mqtt.admin-username}") String username,
            @Value("${voltweave.mqtt.admin-password}") String password
    ) {
        this.objectMapper = objectMapper;
        this.brokerUri = brokerUri;
        this.username = username;
        this.password = password;
    }

    @Override
    public synchronized MqttDeviceCredential provision(Device device) {
        String identity = "device-" + device.id();
        String role = identity + "-role";
        String topicRoot = "voltweave/%s/%s/%s".formatted(
                device.organizationId(), device.siteId(), device.id()
        );
        String secret = secret();

        sendIgnoringError(Map.of("command", "deleteClient", "username", identity));
        sendIgnoringError(Map.of("command", "deleteRole", "rolename", role));
        send(List.of(
                Map.of(
                        "command", "createRole",
                        "rolename", role,
                        "acls", List.of(
                                acl("publishClientSend", topicRoot + "/telemetry"),
                                acl("publishClientSend", topicRoot + "/status"),
                                acl("publishClientSend", topicRoot + "/ack"),
                                acl("subscribePattern", topicRoot + "/command"),
                                acl("publishClientReceive", topicRoot + "/command")
                        )
                ),
                Map.of(
                        "command", "createClient",
                        "username", identity,
                        "password", secret,
                        "clientid", identity,
                        "roles", List.of(Map.of("rolename", role, "priority", 1))
                )
        ));

        return new MqttDeviceCredential(
                brokerUri, identity, secret, identity,
                topicRoot + "/telemetry", topicRoot + "/status",
                topicRoot + "/ack", topicRoot + "/command"
        );
    }

    @Override
    public synchronized void revoke(String username) {
        send(List.of(Map.of("command", "disableClient", "username", username)));
    }

    private void sendIgnoringError(Map<String, Object> command) {
        try {
            send(List.of(command));
        } catch (IllegalStateException ignored) {
            // Replace semantics recover a broker write that committed before a DB rollback.
        }
    }

    private void send(List<Map<String, Object>> commands) {
        try {
            String request = objectMapper.writeValueAsString(Map.of("commands", commands));
            String response = request(request);
            var responses = objectMapper.readTree(response).path("responses");
            for (var item : responses) {
                if (!item.path("error").isMissingNode()) {
                    throw new IllegalStateException(item.path("error").asString());
                }
            }
        } catch (JacksonException exception) {
            throw new IllegalStateException("Invalid Mosquitto Dynamic Security JSON", exception);
        }
    }

    private String request(String request) {
        String clientId = "portfolio-provisioner-" + UUID.randomUUID();
        var response = new AtomicReference<String>();
        var received = new CountDownLatch(1);
        MqttClient client = null;
        try {
            client = new MqttClient(brokerUri, clientId, new MemoryPersistence());
            var options = new MqttConnectOptions();
            options.setUserName(username);
            options.setPassword(password.toCharArray());
            options.setAutomaticReconnect(false);
            options.setCleanSession(true);
            options.setConnectionTimeout((int) RESPONSE_TIMEOUT.toSeconds());
            client.connect(options);
            client.subscribe(RESPONSE_TOPIC, 1, (topic, message) -> {
                response.set(new String(message.getPayload(), UTF_8));
                received.countDown();
            });
            var message = new MqttMessage(request.getBytes(UTF_8));
            message.setQos(1);
            client.publish(CONTROL_TOPIC, message);
            if (!received.await(RESPONSE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Mosquitto Dynamic Security response timed out");
            }
            return response.get();
        } catch (MqttException exception) {
            throw new IllegalStateException("Mosquitto Dynamic Security request failed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Mosquitto Dynamic Security request interrupted", exception);
        } finally {
            close(client);
        }
    }

    private static void close(MqttClient client) {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect(1_000);
            }
            client.close(true);
        } catch (MqttException ignored) {
            // The request result is more useful than a cleanup failure.
        }
    }

    private String secret() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Map<String, Object> acl(String type, String topic) {
        return Map.of("acltype", type, "topic", topic, "priority", 1, "allow", true);
    }
}
