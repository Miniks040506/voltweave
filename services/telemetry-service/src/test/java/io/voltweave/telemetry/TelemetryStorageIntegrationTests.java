package io.voltweave.telemetry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "voltweave.ingress.enabled=false",
        "voltweave.processing.enabled=false"
})
@Import(TimescaleTestConfiguration.class)
@AutoConfigureMockMvc
class TelemetryStorageIntegrationTests {
    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesApplicationHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void migrationCreatesServiceOwnedTimescaleStorage() {
        assertThat(value("SELECT current_user")).isEqualTo("telemetry_app");
        assertThat(value("""
                SELECT extversion FROM pg_extension WHERE extname = 'timescaledb'
                """)).isEqualTo("2.29.0");
        assertThat(value("""
                SELECT count(*) FROM timescaledb_information.hypertables
                WHERE hypertable_schema = 'public'
                  AND hypertable_name = 'telemetry_points'
                """)).isEqualTo("1");
        assertThat(value("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                    'telemetry_points', 'telemetry_dedup',
                    'quarantined_telemetry', 'device_twins',
                    'event_inbox', 'event_outbox'
                  )
                """)).isEqualTo("6");
        assertThat(value("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename IN (
                    'telemetry_points', 'telemetry_dedup',
                    'quarantined_telemetry', 'device_twins',
                    'event_inbox', 'event_outbox'
                  )
                  AND tableowner = current_user
                """)).isEqualTo("6");
    }

    @Test
    void inboxRejectsTheSameEventForTheSameConsumer() {
        UUID eventId = UUID.randomUUID();
        insertInbox(eventId);

        assertThatThrownBy(() -> insertInbox(eventId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void ordinaryDedupTableRejectsARepeatedDeviceSequence() {
        UUID organizationId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-08-12T12:00:00Z");
        insertDedup(organizationId, deviceId, observedAt);

        assertThatThrownBy(() -> insertDedup(organizationId, deviceId, observedAt))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void telemetryConstraintsRejectInvalidSoc() {
        assertThatThrownBy(() -> jdbcClient.sql("""
                INSERT INTO telemetry_points (
                    organization_id, site_id, device_id, sequence_number,
                    observed_at, received_at, device_type,
                    active_power_kw, soc_percent, online
                ) VALUES (
                    :organizationId, :siteId, :deviceId, 1,
                    :observedAt, :receivedAt, 'BATTERY', 2.5, 101, TRUE
                )
                """)
                .param("organizationId", UUID.randomUUID())
                .param("siteId", UUID.randomUUID())
                .param("deviceId", UUID.randomUUID())
                .param("observedAt", timestamp("2026-08-12T12:00:00Z"))
                .param("receivedAt", timestamp("2026-08-12T12:00:01Z"))
                .update()).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertDedup(UUID organizationId, UUID deviceId, Instant observedAt) {
        jdbcClient.sql("""
                INSERT INTO telemetry_dedup (
                    organization_id, device_id, sequence_number,
                    observed_at, expires_at
                ) VALUES (
                    :organizationId, :deviceId, 7, :observedAt, :expiresAt
                )
                """)
                .param("organizationId", organizationId)
                .param("deviceId", deviceId)
                .param("observedAt", Timestamp.from(observedAt))
                .param("expiresAt", Timestamp.from(observedAt.plusSeconds(86_400)))
                .update();
    }

    private void insertInbox(UUID eventId) {
        jdbcClient.sql("""
                INSERT INTO event_inbox (consumer_name, event_id, event_type, received_at)
                VALUES ('telemetry-raw-v1', :eventId, 'TelemetryRawReceived', :receivedAt)
                """)
                .param("eventId", eventId)
                .param("receivedAt", timestamp("2026-08-12T12:00:00Z"))
                .update();
    }

    private static Timestamp timestamp(String instant) {
        return Timestamp.from(Instant.parse(instant));
    }

    private String value(String sql) {
        return jdbcClient.sql(sql).query(String.class).single();
    }
}
