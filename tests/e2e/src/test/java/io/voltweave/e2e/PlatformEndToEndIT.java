package io.voltweave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;

import io.voltweave.simulator.config.DeviceScenario;
import io.voltweave.simulator.config.DeviceType;
import io.voltweave.simulator.config.MqttCredential;
import io.voltweave.simulator.mqtt.MqttDeviceRuntime;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class PlatformEndToEndIT {
    private final PlatformEnvironment environment = new PlatformEnvironment();

    private PlatformClient client;
    private MqttDeviceRuntime simulator;
    private String adminToken;
    private String customerToken;
    private String operatorToken;
    private UUID customerOrganizationId;
    private UUID operatorOrganizationId;
    private UUID siteId;
    private UUID deviceId;

    @BeforeAll
    void startPlatform() throws Exception {
        try {
            environment.start();
            client = new PlatformClient(environment);
            adminToken = client.token("admin", "local-admin-change-me");
            customerToken = client.token("customer", "local-customer-change-me");
            operatorToken = client.token("operator", "local-operator-change-me");
        } catch (Exception exception) {
            environment.close();
            throw exception;
        }
    }

    @AfterAll
    void stopPlatform() throws Exception {
        if (simulator != null) simulator.close();
        environment.close();
    }

    @Test
    @Order(1)
    void telemetryTravelsFromAProvisionedDeviceToItsAuthorizedTwin() throws Exception {
        customerOrganizationId = createOrganization(
                "COMMERCIAL_CUSTOMER", "E2E Customer", "e2e-customer"
        );
        operatorOrganizationId = createOrganization(
                "VPP_OPERATOR", "E2E Operator", "e2e-operator"
        );
        addMember(customerOrganizationId, client.subject(customerToken));
        addMember(operatorOrganizationId, client.subject(operatorToken));

        JsonNode site = client.post("/api/v1/sites", customerToken, """
                {
                  "organizationId": "%s",
                  "name": "E2E Battery Site",
                  "timezone": "Asia/Bangkok",
                  "region": "Bangkok",
                  "country": "TH"
                }
                """.formatted(customerOrganizationId), 201);
        siteId = UUID.fromString(site.path("id").asString());
        client.patch("/api/v1/sites/" + siteId + "/preferences", customerToken, """
                {"vppOptIn": true, "minimumBatteryReservePercent": 20}
                """);

        JsonNode device = client.post("/api/v1/devices", customerToken, """
                {
                  "siteId": "%s",
                  "externalDeviceId": "battery-e2e-01",
                  "type": "BATTERY",
                  "manufacturer": "VoltWeave",
                  "model": "Sandbox Battery",
                  "ratedPowerKw": 20,
                  "battery": {
                    "capacityKwh": 40,
                    "maxChargeKw": 20,
                    "maxDischargeKw": 20,
                    "minSocPercent": 20,
                    "maxSocPercent": 90,
                    "efficiency": 0.95
                  },
                  "evCharger": null
                }
                """.formatted(siteId), 201);
        deviceId = UUID.fromString(device.path("id").asString());

        JsonNode provisioning = client.post(
                "/api/v1/devices/" + deviceId + "/provision",
                customerToken,
                null,
                201,
                java.util.Map.of("Idempotency-Key", "e2e-provision-battery")
        );
        JsonNode credential = provisioning.path("credential");
        assertThat(credential.isObject()).isTrue();

        simulator = new MqttDeviceRuntime(
                "tcp://127.0.0.1:" + environment.mqttPort(),
                scenario(credential),
                1,
                JsonMapper.builder().build(),
                Clock.systemUTC()
        );
        simulator.start();

        JsonNode twin = client.awaitGet(
                "/api/v1/devices/" + deviceId + "/twin",
                customerToken,
                body -> body.path("online").asBoolean()
                        && body.path("sequenceNumber").asLong() >= 1,
                Duration.ofSeconds(45)
        );
        assertThat(twin.path("siteId").asString()).isEqualTo(siteId.toString());
        assertThat(twin.path("deviceType").asString()).isEqualTo("BATTERY");
        assertThat(twin.path("socPercent").asDouble()).isBetween(20.0, 90.0);
    }

    private UUID createOrganization(String type, String name, String tenantCode) throws Exception {
        JsonNode organization = client.post("/api/v1/organizations", adminToken, """
                {
                  "type": "%s",
                  "legalName": "%s Ltd",
                  "displayName": "%s",
                  "tenantCode": "%s",
                  "country": "TH",
                  "timezone": "Asia/Bangkok"
                }
                """.formatted(type, name, name, tenantCode), 201);
        return UUID.fromString(organization.path("id").asString());
    }

    private void addMember(UUID organizationId, String subjectId) throws Exception {
        client.post(
                "/api/v1/organizations/" + organizationId + "/members",
                adminToken,
                """
                        {"subjectId": "%s", "role": "MEMBER"}
                        """.formatted(subjectId),
                201
        );
    }

    private DeviceScenario scenario(JsonNode credential) {
        return new DeviceScenario(
                deviceId,
                DeviceType.BATTERY,
                new MqttCredential(
                        credential.path("username").asString(),
                        credential.path("password").asString(),
                        credential.path("clientId").asString(),
                        credential.path("telemetryTopic").asString(),
                        credential.path("statusTopic").asString(),
                        credential.path("acknowledgementTopic").asString(),
                        credential.path("commandTopic").asString()
                ),
                20,
                70,
                40,
                20,
                90,
                0.95,
                31
        );
    }
}
