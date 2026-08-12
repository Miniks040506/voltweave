package io.voltweave.portfolio.vpp.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import io.voltweave.portfolio.device.application.BatteryConfigurationCommand;
import io.voltweave.portfolio.device.application.DeviceApplicationService;
import io.voltweave.portfolio.device.application.EvChargerConfigurationCommand;
import io.voltweave.portfolio.device.application.RegisterDeviceCommand;
import io.voltweave.portfolio.device.domain.enums.DeviceType;
import io.voltweave.portfolio.organization.application.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import io.voltweave.portfolio.site.application.CreateSiteCommand;
import io.voltweave.portfolio.site.application.SiteApplicationService;
import io.voltweave.portfolio.site.application.UpdateSitePreferenceCommand;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class VppApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private SiteApplicationService siteService;

    @Autowired
    private DeviceApplicationService deviceService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void createsVppWithDisabledPolicyForOperatorOrganization() throws Exception {
        String subject = "fleet-operator";
        UUID organizationId = createOrganization(subject, OrganizationType.VPP_OPERATOR);

        String vppId = createVpp(subject, organizationId, "HCM Fleet");

        mockMvc.perform(get("/api/v1/vpps/{vppId}", vppId).with(operator(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("HCM Fleet"))
                .andExpect(jsonPath("$.automationPolicy.enabled").value(false))
                .andExpect(jsonPath("$.automationPolicy.version").value(1))
                .andExpect(jsonPath("$.memberships").isEmpty());
    }

    @Test
    void enforcesRoleOrganizationTypeAndTenantOwnership() throws Exception {
        String owner = "private-fleet-owner";
        UUID operatorOrganization = createOrganization(owner, OrganizationType.VPP_OPERATOR);
        String vppId = createVpp(owner, operatorOrganization, "Private Fleet");

        mockMvc.perform(get("/api/v1/vpps/{vppId}", vppId)
                        .with(operator("other-operator")))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/vpps")
                        .with(customer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVppRequest(operatorOrganization, "Forbidden Fleet")))
                .andExpect(status().isForbidden());

        UUID customerOrganization = createOrganization(
                "commercial-owner", OrganizationType.COMMERCIAL_CUSTOMER
        );
        mockMvc.perform(post("/api/v1/vpps")
                        .with(operator("commercial-owner"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVppRequest(customerOrganization, "Invalid Fleet")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresCustomerOptInAndOneActiveVppPerSite() throws Exception {
        String customer = "participating-customer";
        UUID siteId = createSite(customer, false);
        String operator = "membership-operator";
        UUID organizationId = createOrganization(operator, OrganizationType.VPP_OPERATOR);
        String firstVpp = createVpp(operator, organizationId, "First Fleet");
        String secondVpp = createVpp(operator, organizationId, "Second Fleet");

        addSite(operator, firstVpp, siteId, 404);
        siteService.updatePreference(
                siteId, new UpdateSitePreferenceCommand(true, 20), customer
        );
        addSite(operator, firstVpp, siteId, 201);
        addSite(operator, secondVpp, siteId, 409);
    }

    @Test
    void calculatesInstalledCapacityAndExcludesOptedOutSite() throws Exception {
        String customer = "capacity-customer";
        UUID siteId = createSite(customer, true);
        registerDevices(customer, siteId);
        String operator = "capacity-operator";
        UUID organizationId = createOrganization(operator, OrganizationType.VPP_OPERATOR);
        String vppId = createVpp(operator, organizationId, "Capacity Fleet");
        addSite(operator, vppId, siteId, 201);

        mockMvc.perform(get("/api/v1/vpps/{vppId}/capacity", vppId)
                        .with(operator(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siteCount").value(1))
                .andExpect(jsonPath("$.deviceCount").value(3))
                .andExpect(jsonPath("$.solarPowerKw").value(4.0))
                .andExpect(jsonPath("$.batteryPowerKw").value(5.0))
                .andExpect(jsonPath("$.evChargerPowerKw").value(7.4))
                .andExpect(jsonPath("$.totalRatedPowerKw").value(16.4));

        siteService.updatePreference(
                siteId, new UpdateSitePreferenceCommand(false, 20), customer
        );
        mockMvc.perform(get("/api/v1/vpps/{vppId}/capacity", vppId)
                        .with(operator(operator)))
                .andExpect(jsonPath("$.siteCount").value(0))
                .andExpect(jsonPath("$.totalRatedPowerKw").value(0));
    }

    @Test
    void removesAndReactivatesTheSameMembership() throws Exception {
        String customer = "returning-customer";
        UUID siteId = createSite(customer, true);
        String operator = "returning-operator";
        UUID organizationId = createOrganization(operator, OrganizationType.VPP_OPERATOR);
        String vppId = createVpp(operator, organizationId, "Returning Fleet");
        addSite(operator, vppId, siteId, 201);

        mockMvc.perform(delete("/api/v1/vpps/{vppId}/sites/{siteId}", vppId, siteId)
                        .with(operator(operator)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberships").isEmpty());
        addSite(operator, vppId, siteId, 201);

        assertThat(jdbcClient.sql("SELECT count(*) FROM vpp_memberships")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void versionsPolicyAndRejectsStaleOrMismatchedUpdates() throws Exception {
        String subject = "policy-operator";
        UUID organizationId = createOrganization(subject, OrganizationType.VPP_OPERATOR);
        String vppId = createVpp(subject, organizationId, "Policy Fleet");

        mockMvc.perform(put("/api/v1/vpps/{vppId}/automation-policy", vppId)
                        .with(operator(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyRequest(1, "PEAK_LIMIT", "500", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.automationPolicy.version").value(2))
                .andExpect(jsonPath("$.automationPolicy.peakImportLimitKw").value(500));

        mockMvc.perform(put("/api/v1/vpps/{vppId}/automation-policy", vppId)
                        .with(operator(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyRequest(1, "PEAK_LIMIT", "500", null)))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/v1/vpps/{vppId}/automation-policy", vppId)
                        .with(operator(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(policyRequest(2, "MANUAL", "500", null)))
                .andExpect(status().isBadRequest());

        assertThat(jdbcClient.sql("SELECT count(*) FROM automation_policies")
                .query(Integer.class).single()).isEqualTo(2);
    }

    private UUID createOrganization(String subject, OrganizationType type) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return organizationService.create(
                new CreateOrganizationCommand(
                        type, "Energy Organization Limited", "Energy " + suffix,
                        "energy-" + suffix, "VN", "Asia/Ho_Chi_Minh"
                ),
                subject
        ).id();
    }

    private UUID createSite(String subject, boolean optIn) {
        UUID organizationId = createOrganization(subject, OrganizationType.COMMERCIAL_CUSTOMER);
        var site = siteService.create(
                new CreateSiteCommand(
                        organizationId, "Home", "Asia/Ho_Chi_Minh", "HCMC", "VN"
                ),
                subject
        ).site();
        if (optIn) {
            siteService.updatePreference(
                    site.id(), new UpdateSitePreferenceCommand(true, 20), subject
            );
        }
        return site.id();
    }

    private String createVpp(String subject, UUID organizationId, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/vpps")
                        .with(operator(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createVppRequest(organizationId, name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void addSite(String subject, String vppId, UUID siteId, int statusCode)
            throws Exception {
        mockMvc.perform(post("/api/v1/vpps/{vppId}/sites/{siteId}", vppId, siteId)
                        .with(operator(subject)))
                .andExpect(status().is(statusCode));
    }

    private void registerDevices(String subject, UUID siteId) {
        register(subject, siteId, DeviceType.SMART_METER, new BigDecimal("22"), null, null);
        register(subject, siteId, DeviceType.SOLAR_INVERTER, new BigDecimal("4"), null, null);
        register(subject, siteId, DeviceType.BATTERY, new BigDecimal("5"),
                new BatteryConfigurationCommand(
                        new BigDecimal("13.5"), new BigDecimal("5"),
                        new BigDecimal("5"), 10, 90, new BigDecimal("0.95")
                ), null);
        register(subject, siteId, DeviceType.EV_CHARGER, new BigDecimal("7.4"), null,
                new EvChargerConfigurationCommand(
                        new BigDecimal("7.4"), new BigDecimal("75"), 80,
                        new BigDecimal("0.92"), Instant.now().plusSeconds(86400)
                ));
    }

    private void register(
            String subject,
            UUID siteId,
            DeviceType type,
            BigDecimal power,
            BatteryConfigurationCommand battery,
            EvChargerConfigurationCommand evCharger
    ) {
        deviceService.register(new RegisterDeviceCommand(
                siteId, UUID.randomUUID().toString(), type, "Demo", "Model",
                power, battery, evCharger
        ), subject);
    }

    private static String createVppRequest(UUID organizationId, String name) {
        return """
                {"organizationId":"%s","name":"%s","region":"VN-HCM"}
                """.formatted(organizationId, name);
    }

    private static String policyRequest(
            int version,
            String trigger,
            String peakLimit,
            String priceThreshold
    ) {
        return """
                {
                  "expectedVersion": %d,
                  "enabled": true,
                  "triggerType": "%s",
                  "approvalMode": "REQUIRE_OPERATOR",
                  "peakImportLimitKw": %s,
                  "priceThreshold": %s,
                  "reserveMarginPercent": 10,
                  "maxDispatchPowerKw": 250,
                  "maxDispatchDurationMinutes": 30,
                  "underDeliveryTolerancePercent": 10,
                  "underDeliveryGraceSeconds": 30,
                  "rebalanceCooldownSeconds": 60,
                  "effectiveFrom": "%s"
                }
                """.formatted(
                version, trigger, peakLimit, priceThreshold, Instant.now()
        );
    }

    private static RequestPostProcessor operator(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_VPP_OPERATOR"));
    }

    private static RequestPostProcessor customer(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
