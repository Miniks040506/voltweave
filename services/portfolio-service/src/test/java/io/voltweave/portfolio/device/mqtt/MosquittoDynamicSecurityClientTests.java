package io.voltweave.portfolio.device.mqtt;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import io.voltweave.portfolio.device.domain.entities.Device;
import io.voltweave.portfolio.device.domain.enums.DeviceType;
import tools.jackson.databind.json.JsonMapper;

class MosquittoDynamicSecurityClientTests {
    private static final String ADMIN = "test-provisioner";
    private static final String ADMIN_PASSWORD = "test-admin-password";

    private static final GenericContainer<?> MOSQUITTO =
            new GenericContainer<>("eclipse-mosquitto:2.0.22")
                    .withExposedPorts(1883)
                    .withEnv("MOSQUITTO_ADMIN_USERNAME", ADMIN)
                    .withEnv("MOSQUITTO_ADMIN_PASSWORD", ADMIN_PASSWORD)
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
    void provisionsScopedCredentialAndRevokesIt() throws Exception {
        String brokerUri = "tcp://localhost:" + MOSQUITTO.getMappedPort(1883);
        var brokerAdmin = new MosquittoDynamicSecurityClient(
                JsonMapper.builder().build(), brokerUri, ADMIN, ADMIN_PASSWORD
        );
        var device = Device.registered(
                UUID.randomUUID(), UUID.randomUUID(), "meter-1", DeviceType.SMART_METER,
                "Shelly", "Pro 3EM", BigDecimal.valueOf(22), Instant.now()
        ).beginProvisioning(Instant.now());

        var credential = brokerAdmin.provision(device);

        assertThat(publish(credential.clientId(), credential.username(),
                credential.password(), credential.telemetryTopic()).getExitCode()).isZero();
        var forbidden = publish(
                credential.clientId(), credential.username(), credential.password(),
                "voltweave/another-tenant/site/device/telemetry"
        );
        assertThat(forbidden.getStderr()).containsIgnoringCase("not authorized");
        assertThat(publish(credential.clientId(), credential.username(),
                "wrong-password", credential.telemetryTopic()).getExitCode()).isNotZero();

        brokerAdmin.revoke(credential.username());

        assertThat(publish(credential.clientId(), credential.username(),
                credential.password(), credential.telemetryTopic()).getExitCode()).isNotZero();
    }

    private static ExecResult publish(
            String clientId,
            String username,
            String password,
            String topic
    ) throws Exception {
        return MOSQUITTO.execInContainer(
                "mosquitto_pub", "-h", "127.0.0.1", "-p", "1883",
                "-i", clientId, "-u", username, "-P", password,
                "-V", "mqttv5", "-q", "1", "-t", topic, "-m", "{}"
        );
    }
}
