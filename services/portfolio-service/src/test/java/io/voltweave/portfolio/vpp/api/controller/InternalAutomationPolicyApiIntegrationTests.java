package io.voltweave.portfolio.vpp.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

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
}
