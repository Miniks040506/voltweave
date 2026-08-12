package io.voltweave.portfolio.audit.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.PostgresTestConfiguration;
import io.voltweave.portfolio.device.application.DeviceApplicationService;
import io.voltweave.portfolio.device.application.DeviceProvisioningApplicationService;
import io.voltweave.portfolio.device.application.command.RegisterDeviceCommand;
import io.voltweave.portfolio.device.domain.enums.DeviceType;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.domain.enums.OrganizationRole;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import io.voltweave.portfolio.site.application.SiteApplicationService;
import io.voltweave.portfolio.site.application.command.CreateSiteCommand;
import io.voltweave.portfolio.site.application.command.UpdateSitePreferenceCommand;
import io.voltweave.portfolio.vpp.application.VppApplicationService;
import io.voltweave.portfolio.vpp.application.command.CreateVppCommand;
import io.voltweave.portfolio.vpp.application.command.UpdateAutomationPolicyCommand;
import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class AuditApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private SiteApplicationService siteService;

    @Autowired
    private DeviceApplicationService deviceService;

    @Autowired
    private DeviceProvisioningApplicationService provisioningService;

    @Autowired
    private VppApplicationService vppService;

    @Test
    void recordsSensitiveMutationsAndDoesNotDuplicateProvisionAudit() throws Exception {
        String customer = "audit-customer";
        UUID customerOrganization = createOrganization(
                customer, OrganizationType.COMMERCIAL_CUSTOMER, "audit-customer"
        );
        organizationService.addMember(
                customerOrganization, customer, "second-member", OrganizationRole.MEMBER
        );
        var site = siteService.create(new CreateSiteCommand(
                customerOrganization, "Audit Home", "Asia/Ho_Chi_Minh", "HCM", "VN"
        ), customer).site();
        siteService.updatePreference(
                site.id(), new UpdateSitePreferenceCommand(true, 25), customer
        );
        var device = deviceService.register(new RegisterDeviceCommand(
                site.id(), "audit-meter", DeviceType.SMART_METER,
                "VoltWeave", "Meter", new BigDecimal("10"), null, null
        ), customer).device();
        provisioningService.provision(device.id(), "audit-provision", customer);
        provisioningService.provision(device.id(), "audit-provision", customer);

        String operator = "audit-operator";
        UUID operatorOrganization = createOrganization(
                operator, OrganizationType.VPP_OPERATOR, "audit-operator"
        );
        var vpp = vppService.create(new CreateVppCommand(
                operatorOrganization, "Audit Fleet", "HCM"
        ), operator).vpp();
        vppService.addSite(vpp.id(), site.id(), operator);
        vppService.removeSite(vpp.id(), site.id(), operator);
        vppService.updatePolicy(vpp.id(), policyCommand(), operator);

        assertThat(jdbcClient.sql("""
                SELECT action FROM audit_entries ORDER BY action
                """).query(String.class).list()).containsExactly(
                        "DEVICE_PROVISION_REQUESTED",
                        "ORGANIZATION_MEMBER_ADDED",
                        "SITE_PREFERENCE_UPDATED",
                        "VPP_AUTOMATION_POLICY_UPDATED",
                        "VPP_SITE_ADDED",
                        "VPP_SITE_REMOVED"
                );

        mockMvc.perform(get("/api/v1/audit")
                        .param("organizationId", customerOrganization.toString())
                        .with(jwt().jwt(token -> token.subject(customer))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].correlationId").isNotEmpty());
    }

    @Test
    void auditHistoryRequiresAdminAndActiveTenantMembership() throws Exception {
        String owner = "audit-owner";
        UUID organizationId = createOrganization(
                owner, OrganizationType.COMMERCIAL_CUSTOMER, "audit-private"
        );
        organizationService.addMember(
                organizationId, owner, "member", OrganizationRole.MEMBER
        );

        mockMvc.perform(get("/api/v1/audit")
                        .param("organizationId", organizationId.toString())
                        .with(jwt().jwt(token -> token.subject(owner))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit")
                        .param("organizationId", organizationId.toString())
                        .with(jwt().jwt(token -> token.subject("other-admin"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void databaseRejectsAuditEntryMutation() {
        String owner = "append-only-owner";
        UUID organizationId = createOrganization(
                owner, OrganizationType.COMMERCIAL_CUSTOMER, "append-only"
        );
        organizationService.addMember(
                organizationId, owner, "append-only-member", OrganizationRole.MEMBER
        );

        assertThatThrownBy(() -> jdbcClient.sql("""
                UPDATE audit_entries SET actor_id = 'tampered'
                WHERE organization_id = :organizationId
                """).param("organizationId", organizationId).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("audit entries are append-only");
    }

    private UUID createOrganization(
            String owner,
            OrganizationType type,
            String tenantPrefix
    ) {
        return organizationService.create(new CreateOrganizationCommand(
                type, "Audit Legal", "Audit Display",
                tenantPrefix + "-" + UUID.randomUUID(), "VN", "Asia/Ho_Chi_Minh"
        ), owner).id();
    }

    private static UpdateAutomationPolicyCommand policyCommand() {
        return new UpdateAutomationPolicyCommand(
                1, true, AutomationTriggerType.PEAK_LIMIT,
                ApprovalMode.REQUIRE_OPERATOR, new BigDecimal("500"), null,
                10, new BigDecimal("250"), 30, 10, 30, 60, Instant.now()
        );
    }
}
