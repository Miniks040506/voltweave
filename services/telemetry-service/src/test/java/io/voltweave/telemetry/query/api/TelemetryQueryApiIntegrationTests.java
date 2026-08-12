package io.voltweave.telemetry.query.api;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.voltweave.telemetry.TimescaleTestConfiguration;
import io.voltweave.telemetry.access.PortfolioAccessClient;

@SpringBootTest(properties = {
        "voltweave.ingress.enabled=false",
        "voltweave.processing.enabled=false",
        "spring.kafka.listener.auto-startup=false"
})
@Import(TimescaleTestConfiguration.class)
@AutoConfigureMockMvc
class TelemetryQueryApiIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000018"
    );
    private static final UUID OTHER_ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000099"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000018"
    );
    private static final UUID DEVICE_ID = UUID.fromString(
            "30000000-0000-0000-0000-000000000018"
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private PortfolioAccessClient accessClient;

    @BeforeEach
    void clearQueryStorage() {
        jdbcClient.sql("TRUNCATE telemetry_points, device_twins").update();
    }

    @Test
    void requiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/sites/{siteId}/live", SITE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void returnsOnlyAuthorizedTenantSiteHistoryInNewestFirstOrder() throws Exception {
        when(accessClient.requireSiteAccess("customer-18", SITE_ID))
                .thenReturn(ORGANIZATION_ID);
        insertPoint(ORGANIZATION_ID, SITE_ID, DEVICE_ID, 1, "2026-08-12T12:00:00Z");
        insertPoint(ORGANIZATION_ID, SITE_ID, DEVICE_ID, 2, "2026-08-12T12:01:00Z");
        insertPoint(
                OTHER_ORGANIZATION_ID, SITE_ID, UUID.randomUUID(), 3,
                "2026-08-12T12:02:00Z"
        );

        mockMvc.perform(get("/api/v1/sites/{siteId}/telemetry", SITE_ID)
                        .with(jwt().jwt(token -> token.subject("customer-18")))
                        .queryParam("from", "2026-08-12T11:59:00Z")
                        .queryParam("to", "2026-08-12T12:03:00Z")
                        .queryParam("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sequenceNumber").value(2))
                .andExpect(jsonPath("$[1].sequenceNumber").value(1));
    }

    @Test
    void rejectsInvalidHistoryWindowAndCrossTenantAccess() throws Exception {
        when(accessClient.requireSiteAccess("customer-18", SITE_ID))
                .thenReturn(ORGANIZATION_ID);

        mockMvc.perform(get("/api/v1/sites/{siteId}/telemetry", SITE_ID)
                        .with(jwt().jwt(token -> token.subject("customer-18")))
                        .queryParam("from", "2026-08-12T12:03:00Z")
                        .queryParam("to", "2026-08-12T12:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid telemetry query"));

        when(accessClient.requireSiteAccess("intruder", SITE_ID))
                .thenThrow(new AccessDeniedException("denied"));
        mockMvc.perform(get("/api/v1/sites/{siteId}/live", SITE_ID)
                        .with(jwt().jwt(token -> token.subject("intruder"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void returnsLatestSiteAndDeviceTwins() throws Exception {
        when(accessClient.requireSiteAccess("customer-18", SITE_ID))
                .thenReturn(ORGANIZATION_ID);
        when(accessClient.requireDeviceAccess("customer-18", DEVICE_ID))
                .thenReturn(ORGANIZATION_ID);
        insertTwin(ORGANIZATION_ID, SITE_ID, DEVICE_ID, 7);

        mockMvc.perform(get("/api/v1/sites/{siteId}/live", SITE_ID)
                        .with(jwt().jwt(token -> token.subject("customer-18"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceId").value(DEVICE_ID.toString()))
                .andExpect(jsonPath("$[0].sequenceNumber").value(7));

        mockMvc.perform(get("/api/v1/devices/{deviceId}/twin", DEVICE_ID)
                        .with(jwt().jwt(token -> token.subject("customer-18"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePowerKw").value(7.125));
    }

    @Test
    void opensAuthorizedSseWithDurableSnapshot() throws Exception {
        when(accessClient.requireSiteAccess("customer-18", SITE_ID))
                .thenReturn(ORGANIZATION_ID);
        insertTwin(ORGANIZATION_ID, SITE_ID, DEVICE_ID, 8);

        mockMvc.perform(get("/api/v1/stream/sites/{siteId}", SITE_ID)
                        .with(jwt().jwt(token -> token.subject("customer-18"))))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith("text/event-stream"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "event:snapshot"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        DEVICE_ID.toString()
                )));
    }

    private void insertPoint(
            UUID organizationId,
            UUID siteId,
            UUID deviceId,
            long sequence,
            String observedAt
    ) {
        jdbcClient.sql("""
                INSERT INTO telemetry_points (
                    organization_id, site_id, device_id, sequence_number,
                    observed_at, received_at, device_type, active_power_kw,
                    online, telemetry_quality
                ) VALUES (
                    :organizationId, :siteId, :deviceId, :sequence,
                    :observedAt, :receivedAt, 'SMART_METER', :power,
                    TRUE, 'VALID'
                )
                """)
                .param("organizationId", organizationId)
                .param("siteId", siteId)
                .param("deviceId", deviceId)
                .param("sequence", sequence)
                .param("observedAt", timestamp(observedAt))
                .param("receivedAt", timestamp("2026-08-12T12:03:00Z"))
                .param("power", sequence + 0.125)
                .update();
    }

    private void insertTwin(
            UUID organizationId,
            UUID siteId,
            UUID deviceId,
            long sequence
    ) {
        jdbcClient.sql("""
                INSERT INTO device_twins (
                    organization_id, site_id, device_id, device_type,
                    last_sequence_number, last_observed_at, last_received_at,
                    active_power_kw, online, telemetry_quality, updated_at
                ) VALUES (
                    :organizationId, :siteId, :deviceId, 'SMART_METER',
                    :sequence, :observedAt, :receivedAt,
                    :power, TRUE, 'VALID', :receivedAt
                )
                """)
                .param("organizationId", organizationId)
                .param("siteId", siteId)
                .param("deviceId", deviceId)
                .param("sequence", sequence)
                .param("observedAt", timestamp("2026-08-12T12:00:00Z"))
                .param("receivedAt", timestamp("2026-08-12T12:00:01Z"))
                .param("power", sequence + 0.125)
                .update();
    }

    private static Timestamp timestamp(String value) {
        return Timestamp.from(Instant.parse(value));
    }
}
