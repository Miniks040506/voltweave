package io.voltweave.portfolio.vpp.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.PostgresTestConfiguration;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import io.voltweave.portfolio.vpp.application.VppApplicationService;
import io.voltweave.portfolio.vpp.application.command.CreateVppCommand;
import io.voltweave.portfolio.vpp.application.command.UpdateAutomationPolicyCommand;
import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class InternalAutomationPolicyApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private VppApplicationService vppService;

    @Test
    void returnsPolicyOnlyForOwningOrganizationToInternalClient() throws Exception {
        String subject = "policy-operator";
        UUID organizationId = organizationService.create(new CreateOrganizationCommand(
                OrganizationType.VPP_OPERATOR, "Policy Legal", "Policy Display",
                "policy-" + UUID.randomUUID(), "VN", "Asia/Ho_Chi_Minh"
        ), subject).id();
        var vpp = vppService.create(new CreateVppCommand(
                organizationId, "Policy Fleet", "HCM"
        ), subject).vpp();

        var internal = jwt().jwt(token -> token.subject("dispatch-service")
                .claim("azp", "voltweave-internal"));
        mockMvc.perform(get("/internal/v1/vpps/{id}/automation-policy", vpp.id())
                        .queryParam("organizationId", organizationId.toString())
                        .with(internal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserveMarginPercent").value(10))
                .andExpect(jsonPath("$.underDeliveryTolerancePercent").value(10))
                .andExpect(jsonPath("$.underDeliveryGraceSeconds").value(30))
                .andExpect(jsonPath("$.rebalanceCooldownSeconds").value(60));

        mockMvc.perform(get("/internal/v1/vpps/{id}/automation-policy", vpp.id())
                        .queryParam("organizationId", UUID.randomUUID().toString())
                        .with(internal))
                .andExpect(status().isNotFound());
    }

    @Test
    void listsOnlyEnabledEffectiveCurrentPolicies() throws Exception {
        String subject = "automation-operator";
        UUID organizationId = organizationService.create(new CreateOrganizationCommand(
                OrganizationType.VPP_OPERATOR, "Automation Legal", "Automation Display",
                "automation-" + UUID.randomUUID(), "VN", "Asia/Ho_Chi_Minh"
        ), subject).id();
        var vpp = vppService.create(new CreateVppCommand(
                organizationId, "Automation Fleet", "HCM"
        ), subject).vpp();
        vppService.updatePolicy(vpp.id(), new UpdateAutomationPolicyCommand(
                1, true, AutomationTriggerType.PEAK_LIMIT, ApprovalMode.AUTO_DISPATCH,
                new BigDecimal("50"), null, 10, new BigDecimal("5"), 30,
                10, 30, 60, Instant.now().minusSeconds(1)
        ), subject);

        mockMvc.perform(get("/internal/v1/automation-policies")
                        .with(jwt().jwt(token -> token.subject("dispatch-service")
                                .claim("azp", "voltweave-internal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].organizationId").value(organizationId.toString()))
                .andExpect(jsonPath("$[0].vppId").value(vpp.id().toString()))
                .andExpect(jsonPath("$[0].triggerType").value("PEAK_LIMIT"))
                .andExpect(jsonPath("$[0].approvalMode").value("AUTO_DISPATCH"))
                .andExpect(jsonPath("$[0].version").value(2));
    }
}
