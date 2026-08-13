package io.voltweave.intelligence.flexibility.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.voltweave.intelligence.PostgresTestConfiguration;
import io.voltweave.intelligence.access.PortfolioAccessClient;
import io.voltweave.intelligence.access.PortfolioFlexibilityClient;
import io.voltweave.intelligence.access.PortfolioFlexibilityClient.PortfolioFlexibilityResource;

@SpringBootTest(properties = {
        "voltweave.projection.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
class FlexibilitySnapshotApiIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000020"
    );
    private static final UUID OTHER_ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000099"
    );
    private static final UUID VPP_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000020"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000020"
    );
    private static final UUID BATTERY_ID = UUID.fromString(
            "50000000-0000-0000-0000-000000000020"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private PortfolioAccessClient accessClient;

    @MockitoBean
    private PortfolioFlexibilityClient portfolioClient;

    @BeforeEach
    void prepareInputs() {
        jdbcClient.sql("""
                TRUNCATE flexibility_candidates, flexibility_snapshots, flexibility_versions,
                         device_telemetry_projection
                """).update();
        when(portfolioClient.resourcesForVpp(VPP_ID)).thenReturn(List.of(battery()));
        Instant now = Instant.now();
        insertTelemetry(BATTERY_ID, "BATTERY", "0", "80", now);
        insertTelemetry(UUID.randomUUID(), "SMART_METER", "6", null, now);
    }

    @Test
    void requiresOperatorRoleAndBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/vpps/{vppId}/flexibility", VPP_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/vpps/{vppId}/flexibility", VPP_ID)
                        .with(jwt().jwt(token -> token.subject("member-20")
                                .claim("realm_access", Map.of(
                                        "roles", List.of("CUSTOMER")
                                )))))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsVersionedSnapshotAndReturnsLatest() throws Exception {
        when(accessClient.requireVppAccess("operator-20", VPP_ID))
                .thenReturn(ORGANIZATION_ID);

        generate().andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.upwardFlexibilityKw").value(5.0))
                .andExpect(jsonPath("$.candidates[0].rawUpwardFlexibilityKw").value(5.0));
        generate().andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/v1/vpps/{vppId}/flexibility", VPP_ID)
                        .with(operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void scopesLatestByOrganizationAndRejectsSnapshotMutation() throws Exception {
        when(accessClient.requireVppAccess("operator-20", VPP_ID))
                .thenReturn(ORGANIZATION_ID);
        generate().andExpect(status().isCreated());

        when(accessClient.requireVppAccess("other-operator", VPP_ID))
                .thenReturn(OTHER_ORGANIZATION_ID);
        mockMvc.perform(get("/api/v1/vpps/{vppId}/flexibility", VPP_ID)
                        .with(jwt().jwt(token -> token.subject("other-operator"))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_VPP_OPERATOR"
                                ))))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertThrows(
                DataAccessException.class,
                () -> jdbcClient.sql("UPDATE flexibility_snapshots SET version = 99").update()
        );
    }

    private org.springframework.test.web.servlet.ResultActions generate() throws Exception {
        return mockMvc.perform(post("/api/v1/vpps/{vppId}/flexibility", VPP_ID)
                .with(operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dispatchDurationMinutes\":60}"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor operatorJwt() {
        return jwt().jwt(token -> token.subject("operator-20"))
                .authorities(new SimpleGrantedAuthority("ROLE_VPP_OPERATOR"));
    }

    private void insertTelemetry(
            UUID deviceId,
            String deviceType,
            String powerKw,
            String socPercent,
            Instant now
    ) {
        jdbcClient.sql("""
                INSERT INTO device_telemetry_projection (
                    organization_id, site_id, device_id, device_type, last_observed_at,
                    last_received_at, active_power_kw, soc_percent, online, quality, updated_at
                ) VALUES (
                    :organizationId, :siteId, :deviceId, :deviceType, :now,
                    :now, :powerKw, :socPercent, TRUE, 'VALID', :now
                )
                """)
                .param("organizationId", ORGANIZATION_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", deviceId)
                .param("deviceType", deviceType)
                .param("now", Timestamp.from(now))
                .param("powerKw", new BigDecimal(powerKw))
                .param("socPercent", socPercent == null ? null : new BigDecimal(socPercent))
                .update();
    }

    private static PortfolioFlexibilityResource battery() {
        return new PortfolioFlexibilityResource(
                ORGANIZATION_ID, SITE_ID, BATTERY_ID, "BATTERY", "PROVISIONED", true,
                new BigDecimal("5"), new BigDecimal("10"), new BigDecimal("5"), 20,
                BigDecimal.ONE, null, null, null, null, null
        );
    }
}
