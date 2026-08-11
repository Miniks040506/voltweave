package io.voltweave.portfolio.site.api.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.voltweave.portfolio.PostgresTestConfiguration;
import io.voltweave.portfolio.organization.application.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class SiteApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationService organizationService;

    @Test
    void createsAndReadsSiteWithSafeDefaults() throws Exception {
        String subject = "customer-subject";
        UUID organizationId = createOrganization(subject);

        String response = mockMvc.perform(post("/api/v1/sites")
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(organizationId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.vppOptIn").value(false))
                .andExpect(jsonPath("$.minimumBatteryReservePercent").value(20))
                .andReturn().getResponse().getContentAsString();
        String siteId = JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/v1/sites/{id}", siteId).with(customer(subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(organizationId.toString()));
    }

    @Test
    void updatesSiteDetailsAndPreferences() throws Exception {
        String subject = "updating-customer";
        String siteId = createSite(subject, createOrganization(subject));

        mockMvc.perform(put("/api/v1/sites/{id}", siteId)
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Updated Office",
                                  "timezone": "Asia/Bangkok",
                                  "region": "Bangkok",
                                  "country": "TH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Office"));

        mockMvc.perform(patch("/api/v1/sites/{id}/preferences", siteId)
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vppOptIn": true,
                                  "minimumBatteryReservePercent": 35
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vppOptIn").value(true))
                .andExpect(jsonPath("$.minimumBatteryReservePercent").value(35));
    }

    @Test
    void hidesSiteFromAnotherOrganizationMember() throws Exception {
        String owner = "site-owner";
        String outsider = "other-tenant-member";
        String siteId = createSite(owner, createOrganization(owner));
        createOrganization(outsider);

        mockMvc.perform(get("/api/v1/sites/{id}", siteId).with(customer(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Site not found"));
    }

    @Test
    void cannotCreateSiteForOrganizationOutsideMembership() throws Exception {
        UUID organizationId = createOrganization("actual-owner");

        mockMvc.perform(post("/api/v1/sites")
                        .with(customer("outsider"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(organizationId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidPreference() throws Exception {
        String subject = "preference-owner";
        String siteId = createSite(subject, createOrganization(subject));

        mockMvc.perform(patch("/api/v1/sites/{id}/preferences", siteId)
                        .with(customer(subject))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "vppOptIn": true,
                                  "minimumBatteryReservePercent": 101
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsOperatorRole() throws Exception {
        mockMvc.perform(post("/api/v1/sites")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_OPERATOR")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    private UUID createOrganization(String subjectId) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return organizationService.create(
                new CreateOrganizationCommand(
                        OrganizationType.COMMERCIAL_CUSTOMER,
                        "Customer Energy Limited",
                        "Customer " + suffix,
                        "customer-" + suffix,
                        "VN",
                        "Asia/Ho_Chi_Minh"
                ),
                subjectId
        ).id();
    }

    private String createSite(String subjectId, UUID organizationId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/sites")
                        .with(customer(subjectId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest(organizationId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private static String createRequest(UUID organizationId) {
        return """
                {
                  "organizationId": "%s",
                  "name": "Home",
                  "timezone": "Asia/Ho_Chi_Minh",
                  "region": "Ho Chi Minh City",
                  "country": "VN"
                }
                """.formatted(organizationId);
    }

    private static RequestPostProcessor customer(String subjectId) {
        return jwt()
                .jwt(token -> token.subject(subjectId))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
