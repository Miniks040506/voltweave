package io.voltweave.portfolio.device.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.PostgresTestConfiguration;
import io.voltweave.portfolio.device.application.DeviceApplicationService;
import io.voltweave.portfolio.device.application.command.BatteryConfigurationCommand;
import io.voltweave.portfolio.device.application.command.RegisterDeviceCommand;
import io.voltweave.portfolio.device.domain.enums.DeviceType;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import io.voltweave.portfolio.site.application.SiteApplicationService;
import io.voltweave.portfolio.site.application.command.CreateSiteCommand;
import io.voltweave.portfolio.site.application.command.UpdateSitePreferenceCommand;
import io.voltweave.portfolio.vpp.application.VppApplicationService;
import io.voltweave.portfolio.vpp.application.command.CreateVppCommand;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class InternalFlexibilityResourceApiIntegrationTests {
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
    void returnsOnlyActiveVppResourcesWithTheEffectiveBatteryReserve() throws Exception {
        String customer = "flex-customer";
        UUID customerOrganization = organization(customer, OrganizationType.COMMERCIAL_CUSTOMER);
        var site = siteService.create(new CreateSiteCommand(
                customerOrganization, "Flex home", "Asia/Ho_Chi_Minh", "HCM", "VN"
        ), customer).site();
        siteService.updatePreference(site.id(), new UpdateSitePreferenceCommand(true, 30), customer);
        var battery = deviceService.register(new RegisterDeviceCommand(
                site.id(), "flex-battery", DeviceType.BATTERY, "VoltWeave", "Battery",
                new BigDecimal("5"), new BatteryConfigurationCommand(
                        new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("5"),
                        20, 100, new BigDecimal("0.9")
                ), null
        ), customer).device();

        String operator = "flex-operator";
        UUID operatorOrganization = organization(operator, OrganizationType.VPP_OPERATOR);
        var vpp = vppService.create(new CreateVppCommand(
                operatorOrganization, "Flex fleet", "HCM"
        ), operator).vpp();
        vppService.addSite(vpp.id(), site.id(), operator);

        mockMvc.perform(get("/internal/v1/vpps/{vppId}/flexibility-resources", vpp.id())
                        .with(jwt().jwt(token -> token
                                .subject("intelligence-service")
                                .claim("azp", "voltweave-internal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deviceId").value(battery.id().toString()))
                .andExpect(jsonPath("$[0].deviceType").value("BATTERY"))
                .andExpect(jsonPath("$[0].minimumSocPercent").value(30))
                .andExpect(jsonPath("$[0].vppOptIn").value(true));
    }

    private UUID organization(String subjectId, OrganizationType type) {
        return organizationService.create(new CreateOrganizationCommand(
                type, "Flex Legal", "Flex Display", subjectId + "-" + UUID.randomUUID(),
                "VN", "Asia/Ho_Chi_Minh"
        ), subjectId).id();
    }
}
