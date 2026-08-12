package io.voltweave.portfolio.device.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.voltweave.portfolio.PostgresTestConfiguration;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import io.voltweave.portfolio.site.application.SiteApplicationService;
import io.voltweave.portfolio.site.application.command.CreateSiteCommand;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class DeviceApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private SiteApplicationService siteService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createsListsAndUpdatesBatteryConfiguration() throws Exception {
        String subject = "battery-owner";
        UUID siteId = createSite(subject);
        String deviceId = createDevice(subject, batteryRequest(siteId, "0.95"));

        mockMvc.perform(get("/api/v1/sites/{siteId}/devices", siteId)
                        .with(customer(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(deviceId))
                .andExpect(jsonPath("$[0].battery.minSocPercent").value(10));

        mockMvc.perform(patch("/api/v1/devices/{deviceId}/settings", deviceId)
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batterySettings("0.97")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.battery.efficiency").value(0.97));
    }

    @Test
    void createsEvChargerWithDepartureTarget() throws Exception {
        String subject = "ev-owner";
        UUID siteId = createSite(subject);

        mockMvc.perform(post("/api/v1/devices")
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evChargerRequest(siteId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.evCharger.targetSocPercent").value(80))
                .andExpect(jsonPath("$.evCharger.chargingEfficiency").value(0.92));
    }

    @Test
    void rejectsConfigurationThatDoesNotMatchDeviceType() throws Exception {
        String subject = "invalid-device-owner";
        UUID siteId = createSite(subject);

        mockMvc.perform(post("/api/v1/devices")
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batteryRequest(siteId, "0.95")
                                .replace("\"BATTERY\"", "\"SMART_METER\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    @Test
    void rejectsDuplicateExternalDeviceIdInsideOrganization() throws Exception {
        String subject = "duplicate-device-owner";
        UUID siteId = createSite(subject);
        String request = meterRequest(siteId);
        createDevice(subject, request);

        mockMvc.perform(post("/api/v1/devices")
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());
    }

    @Test
    void hidesDevicesAcrossTenantBoundary() throws Exception {
        String owner = "private-device-owner";
        UUID siteId = createSite(owner);
        String deviceId = createDevice(owner, meterRequest(siteId));

        mockMvc.perform(get("/api/v1/devices/{deviceId}", deviceId)
                        .with(customer("outsider")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Device not found"));

        mockMvc.perform(get("/api/v1/sites/{siteId}/devices", siteId)
                        .with(customer("outsider")))
                .andExpect(status().isNotFound());
    }

    @Test
    void replaysProvisioningWithTheSameIdempotencyKey() throws Exception {
        String subject = "provisioning-owner";
        UUID siteId = createSite(subject);
        String deviceId = createDevice(subject, meterRequest(siteId));

        String first = provision(subject, deviceId, "provision-request-1", 202);
        String replay = provision(subject, deviceId, "provision-request-1", 202);

        assertThat(JsonPath.<String>read(replay, "$.id"))
                .isEqualTo(JsonPath.<String>read(first, "$.id"));
        assertThat(jdbcClient.sql("SELECT count(*) FROM device_provisioning_requests")
                .query(Integer.class).single()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/devices/{deviceId}", deviceId)
                        .with(customer(subject)))
                .andExpect(jsonPath("$.status").value("PROVISIONING"));
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForAnotherDevice() throws Exception {
        String subject = "idempotency-owner";
        UUID siteId = createSite(subject);
        String first = createDevice(subject, meterRequest(siteId));
        String second = createDevice(subject, meterRequest(siteId));

        provision(subject, first, "shared-key", 202);
        provision(subject, second, "shared-key", 409);
    }

    @Test
    void requiresIdempotencyHeaderAndCustomerRole() throws Exception {
        String subject = "secured-device-owner";
        UUID siteId = createSite(subject);
        String deviceId = createDevice(subject, meterRequest(siteId));

        mockMvc.perform(post("/api/v1/devices/{deviceId}/provision", deviceId)
                        .with(customer(subject)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/devices/{deviceId}", deviceId)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR"))))
                .andExpect(status().isForbidden());
    }

    private UUID createSite(String subjectId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UUID organizationId = organizationService.create(
                new CreateOrganizationCommand(
                        OrganizationType.COMMERCIAL_CUSTOMER, "Device Owner Limited",
                        "Device Owner " + suffix, "device-" + suffix, "VN",
                        "Asia/Ho_Chi_Minh"
                ),
                subjectId
        ).id();
        return siteService.create(
                new CreateSiteCommand(
                        organizationId, "Home", "Asia/Ho_Chi_Minh", "HCMC", "VN"
                ),
                subjectId
        ).site().id();
    }

    private String createDevice(String subject, String request) throws Exception {
        String response = mockMvc.perform(post("/api/v1/devices")
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private String provision(String subject, String deviceId, String key, int statusCode)
            throws Exception {
        return mockMvc.perform(post("/api/v1/devices/{deviceId}/provision", deviceId)
                        .with(customer(subject))
                        .header("Idempotency-Key", key))
                .andExpect(status().is(statusCode))
                .andReturn().getResponse().getContentAsString();
    }

    private static String meterRequest(UUID siteId) {
        return """
                {
                  "siteId": "%s",
                  "externalDeviceId": "%s",
                  "type": "SMART_METER",
                  "manufacturer": "Shelly",
                  "model": "Pro 3EM",
                  "ratedPowerKw": 22
                }
                """.formatted(siteId, UUID.randomUUID());
    }

    private static String batteryRequest(UUID siteId, String efficiency) {
        return """
                {
                  "siteId": "%s",
                  "externalDeviceId": "%s",
                  "type": "BATTERY",
                  "manufacturer": "Tesla",
                  "model": "Powerwall",
                  "ratedPowerKw": 5,
                  "battery": %s
                }
                """.formatted(siteId, UUID.randomUUID(), batterySettingsBody(efficiency));
    }

    private static String evChargerRequest(UUID siteId) {
        return """
                {
                  "siteId": "%s",
                  "externalDeviceId": "%s",
                  "type": "EV_CHARGER",
                  "manufacturer": "Wallbox",
                  "model": "Pulsar Plus",
                  "ratedPowerKw": 7.4,
                  "evCharger": {
                    "maxChargingKw": 7.4,
                    "vehicleBatteryCapacityKwh": 75,
                    "targetSocPercent": 80,
                    "chargingEfficiency": 0.92,
                    "departureAt": "%s"
                  }
                }
                """.formatted(siteId, UUID.randomUUID(), Instant.now().plusSeconds(86400));
    }

    private static String batterySettings(String efficiency) {
        return "{\"battery\":" + batterySettingsBody(efficiency) + "}";
    }

    private static String batterySettingsBody(String efficiency) {
        return """
                {
                  "capacityKwh": 13.5,
                  "maxChargeKw": 5,
                  "maxDischargeKw": 5,
                  "minSocPercent": 10,
                  "maxSocPercent": 90,
                  "efficiency": %s
                }
                """.formatted(efficiency);
    }

    private static RequestPostProcessor customer(String subjectId) {
        return jwt().jwt(token -> token.subject(subjectId))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
