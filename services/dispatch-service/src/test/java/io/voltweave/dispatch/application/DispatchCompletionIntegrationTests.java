package io.voltweave.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.dispatch.PostgresTestConfiguration;
import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.PortfolioAccessClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "voltweave.completion.enabled=false"
})
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
@Transactional
class DispatchCompletionIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final Instant END = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private DispatchCompletionApplicationService service;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioAccessClient portfolioClient;

    @MockitoBean
    private IntelligenceDispatchClient intelligenceClient;

    @Test
    void completesOnceAndPublishesImmutableCompletionFacts() throws Exception {
        UUID dispatchId = seedDispatch("ACTIVE", new BigDecimal("9.500001"));

        service.complete(dispatchId, END.plusSeconds(1));
        service.complete(dispatchId, END.plusSeconds(2));

        assertThat(dispatchStatus(dispatchId)).isEqualTo("PARTIALLY_COMPLETED");
        assertThat(count("event_outbox")).isEqualTo(1);
        JsonNode event = objectMapper.readTree(jdbcClient.sql("""
                SELECT payload::text FROM event_outbox WHERE partition_key = :key
                """).param("key", dispatchId.toString()).query(String.class).single());
        assertThat(event.path("eventType").asString())
                .isEqualTo(EventTypes.DISPATCH_COMPLETED);
        assertThat(event.path("partitionKey").asString()).isEqualTo(dispatchId.toString());
        assertThat(event.path("payload").path("deliveredEnergyKwh").decimalValue())
                .isEqualTo(new BigDecimal("9.500001"));
        assertThat(jdbcClient.sql("SELECT topic FROM event_outbox")
                .query(String.class).single()).isEqualTo(EventTopics.DISPATCH_LIFECYCLE_V1);
    }

    @Test
    void completesDispatchThatMeetsTheScheduledEnergyTarget() {
        UUID dispatchId = seedDispatch("REBALANCING", new BigDecimal("10.000"));

        service.complete(dispatchId, END);

        assertThat(dispatchStatus(dispatchId)).isEqualTo("COMPLETED");
        assertThat(count("event_outbox")).isEqualTo(1);
    }

    @Test
    void exposesFrozenInputOnlyToTheInternalClient() throws Exception {
        UUID dispatchId = seedDispatch("ACTIVE", new BigDecimal("9.500"));
        service.complete(dispatchId, END);
        String path = "/internal/v1/dispatches/" + dispatchId + "/settlement-input";

        mockMvc.perform(get(path).with(jwt().jwt(token -> token
                        .subject("settlement-service")
                        .claim("azp", "voltweave-internal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(ORGANIZATION_ID.toString()))
                .andExpect(jsonPath("$.baselineVersion").value(2))
                .andExpect(jsonPath("$.baselinePoints.length()").value(1))
                .andExpect(jsonPath("$.participants[0].deliveredEnergyKwh").value(9.5));

        mockMvc.perform(get(path).with(jwt().jwt(token -> token
                        .subject("operator")
                        .claim("azp", "voltweave-web"))))
                .andExpect(status().isForbidden());
    }

    private UUID seedDispatch(String status, BigDecimal deliveredEnergy) {
        UUID dispatchId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID forecastId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO dispatches (
                    id, organization_id, vpp_id, optimization_preview_id,
                    optimization_preview_version, type, target_power_kw,
                    required_power_kw, planned_power_kw, scheduled_start_at,
                    scheduled_end_at, status, created_by, created_at
                ) VALUES (:id, :organizationId, :vppId, :previewId, 1,
                    'REDUCE_DEMAND', 10, 10, 10, :startAt, :endAt,
                    :status, 'operator', :createdAt)
                """)
                .param("id", dispatchId)
                .param("organizationId", ORGANIZATION_ID)
                .param("vppId", VPP_ID)
                .param("previewId", UUID.randomUUID())
                .param("startAt", timestamp(END.minus(1, ChronoUnit.HOURS)))
                .param("endAt", timestamp(END))
                .param("status", status)
                .param("createdAt", timestamp(END.minus(2, ChronoUnit.HOURS)))
                .update();
        insertAllocationAndBaseline(dispatchId, deviceId, forecastId);
        insertPerformance(dispatchId, deviceId, deliveredEnergy);
        return dispatchId;
    }

    private void insertAllocationAndBaseline(UUID dispatchId, UUID deviceId, UUID forecastId) {
        jdbcClient.sql("""
                INSERT INTO dispatch_allocations (
                    organization_id, dispatch_id, site_id, device_id, device_type,
                    source_available_power_kw, allocated_power_kw, expected_energy_kwh, score
                ) VALUES (:organizationId, :dispatchId, :siteId, :deviceId,
                    'BATTERY', 10, 10, 10, 1)
                """)
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", dispatchId)
                .param("siteId", SITE_ID)
                .param("deviceId", deviceId)
                .update();
        jdbcClient.sql("""
                INSERT INTO dispatch_baselines (
                    dispatch_id, organization_id, forecast_id, forecast_version,
                    model_name, model_version, source_valid_until, frozen_at
                ) VALUES (:dispatchId, :organizationId, :forecastId, 2,
                    'persistence', 'v1', :endAt, :frozenAt)
                """)
                .param("dispatchId", dispatchId)
                .param("organizationId", ORGANIZATION_ID)
                .param("forecastId", forecastId)
                .param("endAt", timestamp(END))
                .param("frozenAt", timestamp(END.minus(2, ChronoUnit.HOURS)))
                .update();
        jdbcClient.sql("""
                INSERT INTO dispatch_baseline_points (
                    dispatch_id, forecast_at, baseline_grid_import_kw
                ) VALUES (:dispatchId, :forecastAt, 12)
                """)
                .param("dispatchId", dispatchId)
                .param("forecastAt", timestamp(END))
                .update();
    }

    private void insertPerformance(UUID dispatchId, UUID deviceId, BigDecimal deliveredEnergy) {
        jdbcClient.sql("""
                INSERT INTO dispatch_performance_points (
                    id, organization_id, dispatch_id, site_id, device_id,
                    telemetry_event_id, observed_at, target_power_kw,
                    requested_power_kw, actual_power_kw, delivered_power_kw,
                    error_kw, error_percent, cumulative_delivered_energy_kwh,
                    online, quality, recorded_at
                ) VALUES (:id, :organizationId, :dispatchId, :siteId, :deviceId,
                    :eventId, :observedAt, -10, 10, -10, 10, 0, 0,
                    :energy, true, 'VALID', :observedAt)
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", dispatchId)
                .param("siteId", SITE_ID)
                .param("deviceId", deviceId)
                .param("eventId", UUID.randomUUID())
                .param("observedAt", timestamp(END))
                .param("energy", deliveredEnergy)
                .update();
    }

    private String dispatchStatus(UUID dispatchId) {
        return jdbcClient.sql("SELECT status FROM dispatches WHERE id = :id")
                .param("id", dispatchId).query(String.class).single();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
