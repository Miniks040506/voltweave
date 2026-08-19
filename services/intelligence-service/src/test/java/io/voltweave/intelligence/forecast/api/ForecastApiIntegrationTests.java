package io.voltweave.intelligence.forecast.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

@SpringBootTest(properties = {
        "voltweave.projection.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
class ForecastApiIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000019"
    );
    private static final UUID OTHER_ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000099"
    );
    private static final UUID VPP_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000019"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000019"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private PortfolioAccessClient accessClient;

    private Instant targetStart;

    @BeforeEach
    void prepareTrainingData() {
        jdbcClient.sql("""
                TRUNCATE forecast_points, forecasts, forecast_versions,
                         energy_observations, vpp_site_projection, event_inbox
                """).update();
        targetStart = Instant.now().plus(16, ChronoUnit.MINUTES)
                .truncatedTo(ChronoUnit.MINUTES);
        targetStart = targetStart.plusSeconds(
                Math.floorMod(15 - targetStart.atOffset(java.time.ZoneOffset.UTC)
                        .getMinute() % 15, 15) * 60L
        );
        insertMembership(ORGANIZATION_ID, VPP_ID, SITE_ID);
        insertTrainingDay(targetStart.minus(1, ChronoUnit.DAYS), "12", "3");
        insertTrainingDay(targetStart.minus(2, ChronoUnit.DAYS), "9", "1");
    }

    @Test
    void requiresOperatorRoleAndBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/vpps/{vppId}/forecast", VPP_ID))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/vpps/{vppId}/forecast", VPP_ID)
                        .with(jwt().jwt(token -> token
                                .subject("member-19")
                                .claim("realm_access", Map.of(
                                        "roles", List.of("CUSTOMER")
                                )))))
                .andExpect(status().isForbidden());
    }

    @Test
    void generatesVersionedBaselineAndReturnsLatest() throws Exception {
        when(accessClient.requireVppAccess("operator-19", VPP_ID))
                .thenReturn(ORGANIZATION_ID);

        generate().andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.modelVersion").value("1.0"))
                .andExpect(jsonPath("$.points.length()").value(1))
                .andExpect(jsonPath("$.points[0].baselineGridImportKw").value(11.0));
        generate().andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(2));

        mockMvc.perform(get("/api/v1/vpps/{vppId}/forecast", VPP_ID)
                        .with(operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    void excludesAnotherOrganizationsObservationsForTheSameSiteId() throws Exception {
        insertTrainingDay(
                OTHER_ORGANIZATION_ID, targetStart.minus(1, ChronoUnit.DAYS), "100", "0"
        );
        insertTrainingDay(
                OTHER_ORGANIZATION_ID, targetStart.minus(2, ChronoUnit.DAYS), "100", "0"
        );
        when(accessClient.requireVppAccess("operator-19", VPP_ID))
                .thenReturn(ORGANIZATION_ID);

        generate().andExpect(status().isCreated())
                .andExpect(jsonPath("$.points[0].baselineGridImportKw").value(11.0));
    }

    @Test
    void scopesLatestByOrganizationAndRejectsBaselineMutation() throws Exception {
        when(accessClient.requireVppAccess("operator-19", VPP_ID))
                .thenReturn(ORGANIZATION_ID);
        generate().andExpect(status().isCreated());

        when(accessClient.requireVppAccess("other-operator", VPP_ID))
                .thenReturn(OTHER_ORGANIZATION_ID);
        mockMvc.perform(get("/api/v1/vpps/{vppId}/forecast", VPP_ID)
                        .with(jwt().jwt(token -> token
                                .subject("other-operator"))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_VPP_OPERATOR"
                                ))))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertThrows(
                DataAccessException.class,
                () -> jdbcClient.sql("UPDATE forecasts SET model_version = 'changed'")
                        .update()
        );
    }

    private org.springframework.test.web.servlet.ResultActions generate() throws Exception {
        return mockMvc.perform(post("/api/v1/vpps/{vppId}/forecast", VPP_ID)
                .with(operatorJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"horizon":"MINUTES_15","targetStart":"%s"}
                        """.formatted(targetStart)));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor operatorJwt() {
        return jwt().jwt(token -> token.subject("operator-19"))
                .authorities(new SimpleGrantedAuthority("ROLE_VPP_OPERATOR"));
    }

    private void insertMembership(UUID organizationId, UUID vppId, UUID siteId) {
        jdbcClient.sql("""
                INSERT INTO vpp_site_projection (
                    vpp_organization_id, vpp_id, site_id, active, updated_at
                ) VALUES (:organizationId, :vppId, :siteId, TRUE, now())
                """)
                .param("organizationId", organizationId)
                .param("vppId", vppId)
                .param("siteId", siteId)
                .update();
    }

    private void insertTrainingDay(Instant at, String load, String solar) {
        insertTrainingDay(ORGANIZATION_ID, at, load, solar);
    }

    private void insertTrainingDay(
            UUID organizationId,
            Instant at,
            String load,
            String solar
    ) {
        insertObservation(organizationId, UUID.randomUUID(), at, "GRID_IMPORT", load);
        insertObservation(organizationId, UUID.randomUUID(), at, "SOLAR_GENERATION", solar);
    }

    private void insertObservation(
            UUID organizationId,
            UUID deviceId,
            Instant at,
            String type,
            String power
    ) {
        jdbcClient.sql("""
                INSERT INTO energy_observations (
                    organization_id, site_id, device_id, sequence_number,
                    observed_at, received_at, energy_type, power_kw, quality
                ) VALUES (
                    :organizationId, :siteId, :deviceId, 1,
                    :observedAt, :receivedAt, :energyType, :powerKw, 'VALID'
                )
                """)
                .param("organizationId", organizationId)
                .param("siteId", SITE_ID)
                .param("deviceId", deviceId)
                .param("observedAt", Timestamp.from(at))
                .param("receivedAt", Timestamp.from(at.plusSeconds(1)))
                .param("energyType", type)
                .param("powerKw", new BigDecimal(power))
                .update();
    }
}
