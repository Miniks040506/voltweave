package io.voltweave.dispatch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import io.voltweave.dispatch.PostgresTestConfiguration;
import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.Allocation;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.BaselinePoint;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.DispatchInput;
import io.voltweave.dispatch.access.PortfolioAccessClient;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
@Transactional
class DispatchApiIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final UUID PREVIEW_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private PortfolioAccessClient accessClient;

    @MockitoBean
    private IntelligenceDispatchClient intelligenceClient;

    @Test
    void createsAndReadsFrozenScheduledDispatch() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        when(intelligenceClient.input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, startAt, startAt.plusSeconds(1800)
        )).thenReturn(dispatchInput(startAt));

        String response = create("operator", "dispatch-001", startAt, 30, 201);
        String dispatchId = JsonPath.read(response, "$.id");

        assertThat(JsonPath.<String>read(response, "$.status")).isEqualTo("SCHEDULED");
        assertThat(JsonPath.<List<?>>read(response, "$.baseline.points")).hasSize(2);
        assertThat(JsonPath.<List<?>>read(response, "$.allocations")).hasSize(1);
        mockMvc.perform(get("/api/v1/dispatches/{id}", dispatchId).with(operator("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dispatchId))
                .andExpect(jsonPath("$.baseline.forecastVersion").value(3));

        assertThatThrownBy(() -> jdbcClient.sql("""
                UPDATE dispatch_allocations SET allocated_power_kw = 1
                WHERE dispatch_id = :dispatchId
                """).param("dispatchId", UUID.fromString(dispatchId)).update())
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("dispatch execution inputs are immutable");
    }

    @Test
    void replaysSameRequestAndRejectsDifferentPayloadForOneKey() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        when(intelligenceClient.input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, startAt, startAt.plusSeconds(1800)
        )).thenReturn(dispatchInput(startAt));

        String first = create("operator", "same-key", startAt, 30, 201);
        String replay = create("operator", "same-key", startAt, 30, 201);
        create("operator", "same-key", startAt, 45, 409);

        assertThat(JsonPath.<String>read(replay, "$.id"))
                .isEqualTo(JsonPath.<String>read(first, "$.id"));
        verify(intelligenceClient).input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, startAt, startAt.plusSeconds(1800)
        );
    }

    @Test
    void enforcesAuthenticationRoleAndResourceTenant() throws Exception {
        mockMvc.perform(post("/api/v1/dispatches")
                        .header("Idempotency-Key", "unauthenticated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(nextQuarterHour(), 30)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/dispatches").with(customer("customer"))
                        .header("Idempotency-Key", "wrong-role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(nextQuarterHour(), 30)))
                .andExpect(status().isForbidden());

        Instant startAt = nextQuarterHour();
        allow("owner", ORGANIZATION_ID);
        when(intelligenceClient.input(any(), any(), any(), any(), any()))
                .thenReturn(dispatchInput(startAt));
        String dispatchId = JsonPath.read(create("owner", "tenant-check", startAt, 30, 201), "$.id");
        allow("other", UUID.randomUUID());

        mockMvc.perform(get("/api/v1/dispatches/{id}", dispatchId).with(operator("other")))
                .andExpect(status().isForbidden());
    }

    @Test
    void preparesCommandsOnceWithFrozenDeviceTargetAndOutbox() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        when(intelligenceClient.input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, startAt, startAt.plusSeconds(1800)
        )).thenReturn(dispatchInput(startAt));
        String dispatchId = JsonPath.read(
                create("operator", "prepare-command", startAt, 30, 201), "$.id"
        );

        for (int replay = 0; replay < 2; replay++) {
            mockMvc.perform(post("/api/v1/dispatches/{id}/commands", dispatchId)
                            .with(operator("operator")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("REQUESTED"))
                    .andExpect(jsonPath("$[0].targetPowerKw").value(-11.0));
        }

        assertThat(jdbcClient.sql("SELECT status FROM dispatches WHERE id = :id")
                .param("id", UUID.fromString(dispatchId)).query(String.class).single())
                .isEqualTo("PREPARING");
        assertThat(jdbcClient.sql("SELECT count(*) FROM device_commands")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT count(*) FROM event_outbox")
                .query(Integer.class).single()).isEqualTo(1);
    }

    private String create(
            String subject, String key, Instant startAt, int durationMinutes, int statusCode
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/dispatches").with(operator(subject))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(startAt, durationMinutes)))
                .andExpect(status().is(statusCode))
                .andExpect(header().exists("X-Correlation-Id"))
                .andReturn().getResponse().getContentAsString();
    }

    private void allow(String subject, UUID organizationId) {
        when(accessClient.requireVppAccess(subject, VPP_ID)).thenReturn(organizationId);
    }

    private static DispatchInput dispatchInput(Instant startAt) {
        UUID siteId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        return new DispatchInput(
                PREVIEW_ID, 1, ORGANIZATION_ID, VPP_ID,
                new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("11"), true,
                UUID.randomUUID(), 3, "persistence-v1", "1.0", startAt.plusSeconds(3600),
                List.of(new Allocation(
                        siteId, deviceId, "BATTERY", new BigDecimal("12"),
                        new BigDecimal("6"), new BigDecimal("0.9"), new BigDecimal("0.8"),
                        BigDecimal.ONE, new BigDecimal("0.9"), BigDecimal.ONE,
                        new BigDecimal("0.92"), new BigDecimal("11"), true
                )),
                List.of(
                        new BaselinePoint(startAt, new BigDecimal("52")),
                        new BaselinePoint(startAt.plusSeconds(900), new BigDecimal("50"))
                )
        );
    }

    private static String request(Instant startAt, int durationMinutes) {
        return """
                {
                  "vppId": "%s",
                  "optimizationPreviewId": "%s",
                  "type": "REDUCE_DEMAND",
                  "scheduledStartAt": "%s",
                  "durationMinutes": %d
                }
                """.formatted(VPP_ID, PREVIEW_ID, startAt, durationMinutes);
    }

    private static Instant nextQuarterHour() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        long minutes = now.getEpochSecond() / 60;
        return Instant.ofEpochSecond(((minutes / 15) + 2) * 15 * 60);
    }

    private static RequestPostProcessor operator(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_VPP_OPERATOR"));
    }

    private static RequestPostProcessor customer(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }
}
