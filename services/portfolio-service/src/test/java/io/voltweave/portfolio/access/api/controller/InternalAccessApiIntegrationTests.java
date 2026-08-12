package io.voltweave.portfolio.access.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.PostgresTestConfiguration;
import io.voltweave.portfolio.device.application.DeviceApplicationService;
import io.voltweave.portfolio.device.application.command.RegisterDeviceCommand;
import io.voltweave.portfolio.device.domain.enums.DeviceType;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import io.voltweave.portfolio.site.application.SiteApplicationService;
import io.voltweave.portfolio.site.application.command.CreateSiteCommand;
import io.voltweave.portfolio.vpp.application.VppApplicationService;
import io.voltweave.portfolio.vpp.application.command.CreateVppCommand;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class InternalAccessApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private SiteApplicationService siteService;

    @Autowired
    private DeviceApplicationService deviceService;

    @Autowired
    private VppApplicationService vppService;

    @Test
    void resolvesMembershipForEveryPortfolioResourceType() throws Exception {
        String customer = "access-customer";
        UUID customerOrganization = createOrganization(
                customer, OrganizationType.COMMERCIAL_CUSTOMER, "customer"
        );
        var site = siteService.create(new CreateSiteCommand(
                customerOrganization, "Home", "Asia/Ho_Chi_Minh", "HCM", "VN"
        ), customer).site();
        var device = deviceService.register(new RegisterDeviceCommand(
                site.id(), "meter-access", DeviceType.SMART_METER,
                "VoltWeave", "Meter", new BigDecimal("10"), null, null
        ), customer).device();

        String operator = "access-operator";
        UUID operatorOrganization = createOrganization(
                operator, OrganizationType.VPP_OPERATOR, "operator"
        );
        var vpp = vppService.create(new CreateVppCommand(
                operatorOrganization, "Access Fleet", "HCM"
        ), operator).vpp();

        assertAllowed(customer, "ORGANIZATION", customerOrganization, customerOrganization);
        assertAllowed(customer, "SITE", site.id(), customerOrganization);
        assertAllowed(customer, "DEVICE", device.id(), customerOrganization);
        assertAllowed(operator, "VPP", vpp.id(), operatorOrganization);
    }

    @Test
    void deniesUnknownOrCrossTenantSubjectWithoutLeakingOwnership() throws Exception {
        UUID organizationId = createOrganization(
                "private-owner", OrganizationType.COMMERCIAL_CUSTOMER, "private"
        );

        mockMvc.perform(post("/internal/v1/access-checks")
                        .with(jwt().jwt(token -> token
                                .subject("voltweave-service")
                                .claim("azp", "voltweave-internal")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("other-subject", "ORGANIZATION", organizationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.organizationId").doesNotExist())
                .andExpect(jsonPath("$.organizationRole").doesNotExist());
    }

    @Test
    void rejectsUserTokenAndMalformedRequestAtTheBoundary() throws Exception {
        UUID resourceId = UUID.randomUUID();
        mockMvc.perform(post("/internal/v1/access-checks")
                        .with(jwt().jwt(token -> token
                                .subject("normal-user")
                                .claim("azp", "voltweave-web")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("normal-user", "SITE", resourceId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/internal/v1/access-checks")
                        .with(jwt().jwt(token -> token
                                .subject("voltweave-service")
                                .claim("azp", "voltweave-internal")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subjectId":" ","resourceType":"SITE"}
                                """))
                .andExpect(status().isBadRequest());
    }

    private void assertAllowed(
            String subjectId,
            String resourceType,
            UUID resourceId,
            UUID expectedOrganizationId
    ) throws Exception {
        mockMvc.perform(post("/internal/v1/access-checks")
                        .with(jwt().jwt(token -> token
                                .subject("voltweave-service")
                                .claim("azp", "voltweave-internal")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(subjectId, resourceType, resourceId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.organizationId")
                        .value(expectedOrganizationId.toString()))
                .andExpect(jsonPath("$.organizationRole").value("OWNER"));
    }

    private UUID createOrganization(
            String owner,
            OrganizationType type,
            String tenantPrefix
    ) {
        return organizationService.create(new CreateOrganizationCommand(
                type, "Access Legal", "Access Display",
                tenantPrefix + "-" + UUID.randomUUID(), "VN", "Asia/Ho_Chi_Minh"
        ), owner).id();
    }

    private static String request(String subjectId, String resourceType, UUID resourceId) {
        return """
                {"subjectId":"%s","resourceType":"%s","resourceId":"%s"}
                """.formatted(subjectId, resourceType, resourceId);
    }
}
