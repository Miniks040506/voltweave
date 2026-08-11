package io.voltweave.portfolio.organization.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.voltweave.portfolio.PostgresTestConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class OrganizationApiIntegrationTests {
    private static final String VALID_REQUEST = """
            {
              "type": "VPP_OPERATOR",
              "legalName": "VoltWeave Energy Limited",
              "displayName": "VoltWeave North",
              "tenantCode": "north-grid",
              "country": "VN",
              "timezone": "Asia/Ho_Chi_Minh"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void requiresAdminRoleToCreate() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .with(jwt().jwt(token -> token.subject("customer-subject")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void createsOrganizationAddsMemberAndAllowsMemberLookup() throws Exception {
        String response = mockMvc.perform(post("/api/v1/organizations")
                        .with(admin("admin-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tenantCode").value("north-grid"))
                .andReturn().getResponse().getContentAsString();
        String organizationId = JsonPath.read(response, "$.id");

        mockMvc.perform(post("/api/v1/organizations/{id}/members", organizationId)
                        .with(admin("admin-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"member-subject\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/organizations/{id}", organizationId)
                        .with(jwt().jwt(token -> token.subject("member-subject"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(organizationId));
    }

    @Test
    void hidesOrganizationFromUnrelatedSubject() throws Exception {
        String response = mockMvc.perform(post("/api/v1/organizations")
                        .with(admin("owner-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("north-grid", "private-grid")))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(get("/api/v1/organizations/{id}", JsonPath.<String>read(response, "$.id"))
                        .with(jwt().jwt(token -> token.subject("outsider-subject"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidTenantCode() throws Exception {
        mockMvc.perform(post("/api/v1/organizations")
                        .with(admin("admin-subject"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST.replace("north-grid", "INVALID TENANT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin(
            String subjectId
    ) {
        return jwt()
                .jwt(token -> token.subject(subjectId))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
