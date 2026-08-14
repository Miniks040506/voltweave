package io.voltweave.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final List<MqttDeviceRuntime> simulators = new ArrayList<>();
    private String adminToken;
    private String customerToken;
    private String operatorToken;
    private UUID customerOrganizationId;
    private UUID operatorOrganizationId;
    private UUID siteId;
    private UUID deviceId;
    private UUID vppId;
    private UUID optimizationPreviewId;
    private UUID dispatchId;
    private Instant scheduledStartAt;

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
        simulators.reversed().forEach(MqttDeviceRuntime::close);
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

        startSimulator(scenario(deviceId, DeviceType.BATTERY, credential));

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

    @Test
    @Order(2)
    void operatorBuildsAnIntelligencePlanAndSchedulesADispatch() throws Exception {
        JsonNode meter = client.post("/api/v1/devices", customerToken, """
                {
                  "siteId": "%s",
                  "externalDeviceId": "meter-e2e-01",
                  "type": "SMART_METER",
                  "manufacturer": "VoltWeave",
                  "model": "Sandbox Meter",
                  "ratedPowerKw": 30,
                  "battery": null,
                  "evCharger": null
                }
                """.formatted(siteId), 201);
        UUID meterId = UUID.fromString(meter.path("id").asString());
        JsonNode provisioning = client.post(
                "/api/v1/devices/" + meterId + "/provision",
                customerToken,
                null,
                201,
                Map.of("Idempotency-Key", "e2e-provision-meter")
        );
        startSimulator(scenario(
                meterId, DeviceType.SMART_METER, provisioning.path("credential")
        ));
        client.awaitGet(
                "/api/v1/devices/" + meterId + "/twin",
                customerToken,
                body -> body.path("online").asBoolean(),
                Duration.ofSeconds(45)
        );

        JsonNode vpp = client.post("/api/v1/vpps", operatorToken, """
                {
                  "organizationId": "%s",
                  "name": "Bangkok E2E VPP",
                  "region": "Bangkok"
                }
                """.formatted(operatorOrganizationId), 201);
        vppId = UUID.fromString(vpp.path("id").asString());
        client.post(
                "/api/v1/vpps/" + vppId + "/sites/" + siteId,
                operatorToken,
                null,
                201
        );

        scheduledStartAt = currentQuarterHour().plus(1, ChronoUnit.DAYS);
        JsonNode forecast = client.awaitPost(
                "/api/v1/vpps/" + vppId + "/forecast",
                operatorToken,
                """
                        {"horizon": "MINUTES_15", "targetStart": "%s"}
                        """.formatted(scheduledStartAt),
                201,
                Map.of(),
                body -> body.path("points").size() == 1,
                Duration.ofSeconds(60)
        );
        assertThat(forecast.path("points").get(0)
                .path("baselineGridImportKw").asDouble()).isPositive();

        JsonNode flexibility = client.awaitPost(
                "/api/v1/vpps/" + vppId + "/flexibility",
                operatorToken,
                "{\"dispatchDurationMinutes\": 15}",
                201,
                Map.of(),
                body -> body.path("upwardFlexibilityKw").asDouble() > 0,
                Duration.ofSeconds(60)
        );
        assertThat(flexibility.path("candidates").size()).isGreaterThanOrEqualTo(1);
        BigDecimal targetPower = flexibility.path("upwardFlexibilityKw").decimalValue()
                .divide(BigDecimal.TWO, 3, RoundingMode.DOWN)
                .max(new BigDecimal("0.001"));

        JsonNode preview = client.post(
                "/api/v1/vpps/" + vppId + "/optimization-preview",
                operatorToken,
                "{\"targetPowerKw\": %s, \"reserveMarginPercent\": 0}"
                        .formatted(targetPower.toPlainString()),
                200
        );
        assertThat(preview.path("feasible").asBoolean()).isTrue();
        optimizationPreviewId = UUID.fromString(preview.path("id").asString());

        JsonNode dispatch = client.post(
                "/api/v1/dispatches",
                operatorToken,
                dispatchRequest(),
                201,
                Map.of("Idempotency-Key", "e2e-dispatch-01")
        );
        dispatchId = UUID.fromString(dispatch.path("id").asString());
        assertThat(dispatch.path("status").asString()).isEqualTo("SCHEDULED");
        assertThat(dispatch.path("allocations").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @Order(3)
    void tenantBoundariesAndIdempotencyHoldAcrossTheGateway() throws Exception {
        JsonNode replayedProvisioning = client.post(
                "/api/v1/devices/" + deviceId + "/provision",
                customerToken,
                null,
                200,
                Map.of("Idempotency-Key", "e2e-provision-battery")
        );
        assertThat(replayedProvisioning.path("deviceId").asString())
                .isEqualTo(deviceId.toString());
        assertThat(replayedProvisioning.path("credential").isNull()).isTrue();

        JsonNode replayedDispatch = client.post(
                "/api/v1/dispatches",
                operatorToken,
                dispatchRequest(),
                201,
                Map.of("Idempotency-Key", "e2e-dispatch-01")
        );
        assertThat(replayedDispatch.path("id").asString()).isEqualTo(dispatchId.toString());

        String conflictingRequest = dispatchRequest().replace(
                "\"durationMinutes\": 15", "\"durationMinutes\": 30"
        );
        PlatformClient.Response conflict = client.send(
                "POST",
                "/api/v1/dispatches",
                operatorToken,
                conflictingRequest,
                Map.of("Idempotency-Key", "e2e-dispatch-01")
        );
        assertThat(conflict.status()).isEqualTo(409);

        UUID outsiderOrganizationId = createOrganization(
                "COMMERCIAL_CUSTOMER", "Outsider Customer", "e2e-outsider"
        );
        JsonNode outsiderSite = client.post("/api/v1/sites", adminToken, """
                {
                  "organizationId": "%s",
                  "name": "Outsider Site",
                  "timezone": "Asia/Bangkok",
                  "region": "Bangkok",
                  "country": "TH"
                }
                """.formatted(outsiderOrganizationId), 201);

        assertThat(client.send(
                "GET",
                "/api/v1/sites/" + outsiderSite.path("id").asString(),
                customerToken,
                null,
                Map.of()
        ).status()).isEqualTo(404);
        assertThat(client.send(
                "GET", "/api/v1/sites/" + siteId, operatorToken, null, Map.of()
        ).status()).isEqualTo(403);
        assertThat(client.send(
                "GET", "/api/v1/vpps/" + vppId, customerToken, null, Map.of()
        ).status()).isEqualTo(403);
        assertThat(client.send(
                "GET",
                "/api/v1/sites/" + siteId,
                "eyJhbGciOiJub25lIn0.eyJzdWIiOiJhdHRhY2tlciJ9.",
                null,
                Map.of()
        ).status()).isEqualTo(401);
    }

    @Test
    @Order(4)
    void preparedCommandsAndStateSurviveAServiceRestart() throws Exception {
        environment.restartDispatch();
        JsonNode restored = client.get("/api/v1/dispatches/" + dispatchId, operatorToken);
        assertThat(restored.path("status").asString()).isEqualTo("SCHEDULED");

        JsonNode commands = client.post(
                "/api/v1/dispatches/" + dispatchId + "/commands",
                operatorToken,
                null,
                200
        );
        assertThat(commands.isArray()).isTrue();
        assertThat(commands.size()).isGreaterThanOrEqualTo(1);
        assertThat(commands.path(0).path("status").asString()).isEqualTo("REQUESTED");

        environment.restartDispatch();
        JsonNode recovered = client.get("/api/v1/dispatches/" + dispatchId, operatorToken);
        assertThat(recovered.path("status").asString()).isEqualTo("PREPARING");

        JsonNode replayedCommands = client.post(
                "/api/v1/dispatches/" + dispatchId + "/commands",
                operatorToken,
                null,
                200
        );
        assertThat(replayedCommands.path(0).path("id").asString())
                .isEqualTo(commands.path(0).path("id").asString());
        assertThat(replayedCommands.path(0).path("status").asString())
                .isEqualTo("REQUESTED");
    }

    @Test
    @Order(5)
    void everyServiceExposesPrometheusMetrics() throws Exception {
        for (String service : List.of(
                "gateway", "portfolio", "telemetry", "intelligence", "dispatch", "settlement"
        )) {
            String metrics = environment.serviceMetrics(service);
            assertThat(metrics).contains("jvm_memory_used_bytes");
            assertThat(metrics).contains("application=\"" + serviceName(service) + "\"");
        }
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

    private void startSimulator(DeviceScenario scenario) throws Exception {
        var simulator = new MqttDeviceRuntime(
                "tcp://127.0.0.1:" + environment.mqttPort(),
                scenario,
                1,
                JsonMapper.builder().build(),
                Clock.systemUTC()
        );
        simulator.start();
        simulators.add(simulator);
    }

    private DeviceScenario scenario(UUID id, DeviceType type, JsonNode credential) {
        return new DeviceScenario(
                id,
                type,
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

    private String dispatchRequest() {
        return """
                {
                  "vppId": "%s",
                  "optimizationPreviewId": "%s",
                  "type": "REDUCE_DEMAND",
                  "scheduledStartAt": "%s",
                  "durationMinutes": 15
                }
                """.formatted(vppId, optimizationPreviewId, scheduledStartAt);
    }

    private static Instant currentQuarterHour() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        return Instant.ofEpochSecond(now.getEpochSecond() - now.getEpochSecond() % 900);
    }

    private static String serviceName(String service) {
        return "gateway".equals(service) ? "api-gateway" : service + "-service";
    }
}
