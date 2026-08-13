package io.voltweave.intelligence.optimization.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
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

@SpringBootTest(properties = {
        "voltweave.projection.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
class OptimizationPreviewApiIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000021"
    );
    private static final UUID OTHER_ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000099"
    );
    private static final UUID VPP_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000021"
    );
    private static final UUID SNAPSHOT_ID = UUID.fromString(
            "60000000-0000-0000-0000-000000000021"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000021"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private PortfolioAccessClient accessClient;

    @MockitoBean
    private PortfolioFlexibilityClient flexibilityClient;

    @BeforeEach
    void prepareSnapshot() {
        jdbcClient.sql("""
                TRUNCATE optimization_candidates, optimization_previews, optimization_versions,
                         flexibility_candidates, flexibility_snapshots, flexibility_versions
                """).update();
        insertSnapshot();
        insertCandidate("50000000-0000-0000-0000-000000000021", "BATTERY", "4", "4", null);
        insertCandidate("50000000-0000-0000-0000-000000000022", "EV_CHARGER", "3", "2", null);
        insertCandidate("50000000-0000-0000-0000-000000000023", "BATTERY", "0", "0", "SITE_OPTED_OUT");
        when(accessClient.requireVppAccess("operator-21", VPP_ID))
                .thenReturn(ORGANIZATION_ID);
    }

    @Test
    void requiresBearerTokenAndOperatorRole() throws Exception {
        mockMvc.perform(post(path()).contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(path()).with(jwt().authorities(
                                new SimpleGrantedAuthority("ROLE_CUSTOMER")
                        )).contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void createsVersionedImmutablePreviewFromLatestSnapshot() throws Exception {
        generate().andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.flexibilitySnapshotId").value(SNAPSHOT_ID.toString()))
                .andExpect(jsonPath("$.requiredPowerKw").value(5.5))
                .andExpect(jsonPath("$.plannedPowerKw").value(5.5))
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.weightVersion").value("V1"))
                .andExpect(jsonPath("$.candidates[0].allocatedPowerKw").value(4.0));
        generate().andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));

        org.junit.jupiter.api.Assertions.assertThrows(
                DataAccessException.class,
                () -> jdbcClient.sql("UPDATE optimization_previews SET version = 99").update()
        );
    }

    @Test
    void rejectsInvalidPayloadAndMissingTenantSnapshot() throws Exception {
        mockMvc.perform(post(path()).with(operatorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetPowerKw\":0,\"reserveMarginPercent\":101}"))
                .andExpect(status().isBadRequest());

        when(accessClient.requireVppAccess("operator-21", VPP_ID))
                .thenReturn(OTHER_ORGANIZATION_ID);
        generate().andExpect(status().isUnprocessableContent());
    }

    @Test
    void protectsDispatchInputWithInternalClientIdentity() throws Exception {
        generate().andExpect(status().isOk());
        UUID previewId = jdbcClient.sql("SELECT id FROM optimization_previews")
                .query(UUID.class).single();
        String internalPath = "/internal/v1/vpps/" + VPP_ID + "/dispatch-inputs/" + previewId;

        mockMvc.perform(get(internalPath).with(operatorJwt())
                        .param("organizationId", ORGANIZATION_ID.toString())
                        .param("startAt", Instant.now().plusSeconds(900).toString())
                        .param("endAt", Instant.now().plusSeconds(1800).toString()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(internalPath).with(jwt().jwt(token -> token
                                .claim("azp", "voltweave-internal")))
                        .param("organizationId", ORGANIZATION_ID.toString())
                        .param("startAt", Instant.now().plusSeconds(900).toString())
                        .param("endAt", Instant.now().plusSeconds(1800).toString()))
                .andExpect(status().isUnprocessableContent());
    }

    private org.springframework.test.web.servlet.ResultActions generate() throws Exception {
        return mockMvc.perform(post(path()).with(operatorJwt())
                .contentType(MediaType.APPLICATION_JSON).content(validRequest()));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor operatorJwt() {
        return jwt().jwt(token -> token.subject("operator-21"))
                .authorities(new SimpleGrantedAuthority("ROLE_VPP_OPERATOR"));
    }

    private String path() {
        return "/api/v1/vpps/" + VPP_ID + "/optimization-preview";
    }

    private static String validRequest() {
        return "{\"targetPowerKw\":5,\"reserveMarginPercent\":10}";
    }

    private void insertSnapshot() {
        Instant now = Instant.now();
        jdbcClient.sql("""
                INSERT INTO flexibility_snapshots (
                    id, vpp_organization_id, vpp_id, version, dispatch_duration_seconds,
                    generated_at, valid_until, upward_flexibility_kw, available_energy_kwh
                ) VALUES (
                    :id, :organizationId, :vppId, 1, 3600,
                    :generatedAt, :validUntil, 7, 6
                )
                """)
                .param("id", SNAPSHOT_ID)
                .param("organizationId", ORGANIZATION_ID)
                .param("vppId", VPP_ID)
                .param("generatedAt", Timestamp.from(now.minusSeconds(30)))
                .param("validUntil", Timestamp.from(now.plusSeconds(300)))
                .update();
    }

    private void insertCandidate(
            String deviceId,
            String type,
            String power,
            String energy,
            String reason
    ) {
        jdbcClient.sql("""
                INSERT INTO flexibility_candidates (
                    vpp_organization_id, snapshot_id, site_id, device_id, device_type,
                    raw_upward_flexibility_kw, upward_flexibility_kw,
                    available_energy_kwh, limiting_reason
                ) VALUES (
                    :organizationId, :snapshotId, :siteId, :deviceId, :type,
                    :power, :power, :energy, :reason
                )
                """)
                .param("organizationId", ORGANIZATION_ID)
                .param("snapshotId", SNAPSHOT_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", UUID.fromString(deviceId))
                .param("type", type)
                .param("power", new BigDecimal(power))
                .param("energy", new BigDecimal(energy))
                .param("reason", reason)
                .update();
    }
}
