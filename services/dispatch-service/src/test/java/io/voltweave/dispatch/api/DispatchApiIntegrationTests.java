package io.voltweave.dispatch.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
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
import java.time.Duration;
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
import io.voltweave.dispatch.application.AutomationApplicationService;
import io.voltweave.dispatch.application.model.AutomationPlan;
import io.voltweave.dispatch.application.model.AutomationPolicy;

@SpringBootTest(properties = "voltweave.automation.evaluation-delay=1h")
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

    @Autowired
    private AutomationApplicationService automationService;

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
    void listsDispatchesForAnAuthorizedVpp() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        when(intelligenceClient.input(any(), any(), any(), any(), any()))
                .thenReturn(dispatchInput(startAt));
        String firstId = JsonPath.read(
                create("operator", "history-first", startAt, 30, 201), "$.id"
        );
        String secondId = JsonPath.read(
                create("operator", "history-second", startAt.plusSeconds(900), 30, 201), "$.id"
        );

        String response = mockMvc.perform(get("/api/v1/dispatches")
                        .queryParam("vppId", VPP_ID.toString())
                        .with(operator("operator")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(response, "$[*].id"))
                .contains(firstId, secondId);
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

    @Test
    void listsAndApprovesOperatorAutomationCandidate() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        when(intelligenceClient.input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, startAt, startAt.plusSeconds(1800)
        )).thenReturn(dispatchInput(startAt));
        var policy = new AutomationPolicy(
                UUID.randomUUID(), ORGANIZATION_ID, VPP_ID, "PEAK_LIMIT",
                "REQUIRE_OPERATOR", new BigDecimal("50"), null, 10,
                new BigDecimal("10"), 30, 2
        );
        var dispatch = automationService.createCandidate(policy, new AutomationPlan(
                startAt, Duration.ofMinutes(30), PREVIEW_ID, true
        )).orElseThrow();

        mockMvc.perform(get("/api/v1/automation-candidates")
                        .queryParam("vppId", VPP_ID.toString())
                        .with(operator("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].policyId").value(policy.id().toString()))
                .andExpect(jsonPath("$[0].dispatch.id").value(dispatch.id().toString()))
                .andExpect(jsonPath("$[0].dispatch.status").value("SCHEDULED"));

        mockMvc.perform(post("/api/v1/automation-candidates/{id}/approve", dispatch.id())
                        .with(operator("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REQUESTED"));

        assertThat(jdbcClient.sql("SELECT status FROM dispatches WHERE id = :id")
                .param("id", dispatch.id()).query(String.class).single())
                .isEqualTo("PREPARING");

        var rejected = automationService.createCandidate(new AutomationPolicy(
                UUID.randomUUID(), ORGANIZATION_ID, VPP_ID, "PEAK_LIMIT",
                "REQUIRE_OPERATOR", new BigDecimal("50"), null, 10,
                new BigDecimal("10"), 30, 3
        ), new AutomationPlan(
                startAt, Duration.ofMinutes(30), PREVIEW_ID, true
        )).orElseThrow();
        mockMvc.perform(post("/api/v1/automation-candidates/{id}/reject", rejected.id())
                        .with(operator("operator")))
                .andExpect(status().isNoContent());
        assertThat(jdbcClient.sql("SELECT status FROM dispatches WHERE id = :id")
                .param("id", rejected.id()).query(String.class).single())
                .isEqualTo("CANCELLED");
    }

    @Test
    void readsEmptyPerformanceWithRequestedAllocationAndEnforcesTenant() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        when(intelligenceClient.input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, startAt, startAt.plusSeconds(1800)
        )).thenReturn(dispatchInput(startAt));
        String dispatchId = JsonPath.read(
                create("operator", "performance", startAt, 30, 201), "$.id"
        );

        mockMvc.perform(get("/api/v1/dispatches/{id}/performance", dispatchId)
                        .with(operator("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchId").value(dispatchId))
                .andExpect(jsonPath("$.requestedPowerKw").value(11.0))
                .andExpect(jsonPath("$.deliveredPowerKw").value(0.0))
                .andExpect(jsonPath("$.errorKw").value(11.0))
                .andExpect(jsonPath("$.achievementPercent").value(0.0))
                .andExpect(jsonPath("$.deliveredEnergyKwh").value(0.0))
                .andExpect(jsonPath("$.points").isEmpty());

        allow("other", UUID.randomUUID());
        mockMvc.perform(get("/api/v1/dispatches/{id}/performance", dispatchId)
                        .with(operator("other")))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED
    )
    void rejectsOverlappingDispatchForReservedDevice() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        DispatchInput input = dispatchInput(startAt);
        when(intelligenceClient.input(any(), any(), any(), any(), any())).thenReturn(input);
        String first = JsonPath.read(
                create("operator", "reservation-first", startAt, 30, 201), "$.id"
        );
        String second = JsonPath.read(
                create("operator", "reservation-second", startAt, 30, 201), "$.id"
        );

        mockMvc.perform(post("/api/v1/dispatches/{id}/commands", first)
                        .with(operator("operator")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/dispatches/{id}/commands", second)
                        .with(operator("operator")))
                .andExpect(status().isUnprocessableEntity());

        assertThat(jdbcClient.sql("SELECT count(*) FROM device_reservations")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT count(*) FROM device_commands")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void rejectingCandidateReleasesItsDeviceReservation() throws Exception {
        Instant startAt = nextQuarterHour();
        allow("operator", ORGANIZATION_ID);
        when(intelligenceClient.input(any(), any(), any(), any(), any()))
                .thenReturn(dispatchInput(startAt));
        var policy = new AutomationPolicy(
                UUID.randomUUID(), ORGANIZATION_ID, VPP_ID, "PEAK_LIMIT",
                "REQUIRE_OPERATOR", new BigDecimal("50"), null, 10,
                new BigDecimal("10"), 30, 1
        );
        var cancelled = automationService.createCandidate(policy, new AutomationPlan(
                startAt, Duration.ofMinutes(30), PREVIEW_ID, true
        )).orElseThrow();
        UUID deviceId = jdbcClient.sql("""
                SELECT device_id FROM dispatch_allocations WHERE dispatch_id = :dispatchId
                """).param("dispatchId", cancelled.id()).query(UUID.class).single();
        insertReservation(cancelled.id(), deviceId, startAt);

        assertThat(automationService.rejectCandidate(ORGANIZATION_ID, cancelled.id())).isTrue();

        assertThat(jdbcClient.sql("SELECT count(*) FROM device_reservations")
                .query(Integer.class).single()).isZero();
        UUID replacementDispatchId = UUID.fromString(JsonPath.read(
                create("operator", "after-cancellation", startAt, 30, 201), "$.id"
        ));
        insertReservation(replacementDispatchId, deviceId, startAt);
        assertThat(jdbcClient.sql("SELECT count(*) FROM device_reservations")
                .query(Integer.class).single()).isEqualTo(1);
    }

    private void insertReservation(UUID dispatchId, UUID deviceId, Instant startAt) {
        jdbcClient.sql("""
                INSERT INTO device_reservations (
                    id, organization_id, dispatch_id, device_id,
                    reserved_from, reserved_until, created_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :deviceId,
                    :reservedFrom, :reservedUntil, CURRENT_TIMESTAMP
                )
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", dispatchId)
                .param("deviceId", deviceId)
                .param("reservedFrom", java.sql.Timestamp.from(startAt))
                .param("reservedUntil", java.sql.Timestamp.from(startAt.plusSeconds(1800)))
                .update();
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
        doReturn(organizationId).when(accessClient).requireVppAccess(subject, VPP_ID);
    }

    private static DispatchInput dispatchInput(Instant startAt) {
        UUID siteId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        return new DispatchInput(
                PREVIEW_ID, 1, ORGANIZATION_ID, VPP_ID,
                Duration.ofMinutes(30).toSeconds(),
                new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("11"), true,
                UUID.randomUUID(), 3, "persistence-v1", "1.0", startAt.plusSeconds(3600),
                List.of(new Allocation(
                        siteId, deviceId, "BATTERY", new BigDecimal("12"),
                        new BigDecimal("12"),
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
