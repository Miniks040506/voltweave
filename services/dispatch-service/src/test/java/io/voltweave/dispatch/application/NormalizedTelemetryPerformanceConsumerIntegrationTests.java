package io.voltweave.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.contracts.events.v1.TelemetryQualityV1;
import io.voltweave.dispatch.PostgresTestConfiguration;
import io.voltweave.dispatch.application.model.DispatchPerformance;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresTestConfiguration.class)
@Transactional
class NormalizedTelemetryPerformanceConsumerIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DISPATCH_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID BATTERY_ID = UUID.randomUUID();
    private static final UUID EV_ID = UUID.randomUUID();
    private static final Instant START = Instant.parse("2026-08-13T10:00:00Z");

    @Autowired
    private NormalizedTelemetryPerformanceConsumer consumer;

    @Autowired
    private PerformanceApplicationService performanceService;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void seedActiveDispatch() {
        jdbcClient.sql("""
                INSERT INTO dispatches (
                    id, organization_id, vpp_id, optimization_preview_id,
                    optimization_preview_version, type, target_power_kw,
                    required_power_kw, planned_power_kw, scheduled_start_at,
                    scheduled_end_at, status, created_by, created_at
                ) VALUES (
                    :id, :organizationId, :vppId, :previewId, 1,
                    'REDUCE_DEMAND', 10, 10, 10, :startAt, :endAt,
                    'ACTIVE', 'operator', :createdAt
                )
                """)
                .param("id", DISPATCH_ID)
                .param("organizationId", ORGANIZATION_ID)
                .param("vppId", UUID.randomUUID())
                .param("previewId", UUID.randomUUID())
                .param("startAt", java.sql.Timestamp.from(START))
                .param("endAt", java.sql.Timestamp.from(START.plusSeconds(900)))
                .param("createdAt", java.sql.Timestamp.from(START.minusSeconds(60)))
                .update();
        insertAllocation(BATTERY_ID, "BATTERY", "8", "5", "-5");
        insertAllocation(EV_ID, "EV_CHARGER", "7", "5", "2");
    }

    @Test
    void measuresBatteryDeliveryEnergyOfflineStateAndReplay() throws Exception {
        consumer.consume(telemetry(
                UUID.randomUUID(), BATTERY_ID, 1, START, "BATTERY", "-4", true,
                TelemetryQualityV1.VALID
        ));
        ConsumerRecord<String, String> second = telemetry(
                UUID.randomUUID(), BATTERY_ID, 2, START.plusSeconds(60),
                "BATTERY", "-6", true, TelemetryQualityV1.VALID
        );
        consumer.consume(second);
        consumer.consume(second);
        consumer.consume(telemetry(
                UUID.randomUUID(), BATTERY_ID, 3, START.plusSeconds(120),
                "BATTERY", "-6", false, TelemetryQualityV1.VALID
        ));

        DispatchPerformance performance = performanceService
                .find(ORGANIZATION_ID, DISPATCH_ID).orElseThrow();

        assertThat(performance.points()).hasSize(3);
        assertThat(performance.points().get(0).deliveredPowerKw())
                .isEqualByComparingTo("4.000");
        assertThat(performance.points().get(0).errorPercent())
                .isEqualByComparingTo("20.000");
        assertThat(performance.points().get(1).cumulativeDeliveredEnergyKwh())
                .isEqualByComparingTo("0.083333");
        assertThat(performance.points().get(2).deliveredPowerKw())
                .isEqualByComparingTo("0.000");
        assertThat(performance.deliveredEnergyKwh()).isEqualByComparingTo("0.133333");
        assertThat(performance.deliveredPowerKw()).isEqualByComparingTo("0.000");
        assertThat(performance.errorKw()).isEqualByComparingTo("5.000");
        assertThat(count("event_inbox")).isEqualTo(3);
    }

    @Test
    void measuresEvCurtailmentAndIgnoresNonValidTelemetry() throws Exception {
        consumer.consume(telemetry(
                UUID.randomUUID(), EV_ID, 1, START, "EV_CHARGER", "2", true,
                TelemetryQualityV1.VALID
        ));
        consumer.consume(telemetry(
                UUID.randomUUID(), EV_ID, 2, START.plusSeconds(60),
                "EV_CHARGER", "1", true, TelemetryQualityV1.STALE
        ));

        DispatchPerformance performance = performanceService
                .find(ORGANIZATION_ID, DISPATCH_ID).orElseThrow();

        assertThat(performance.points()).singleElement().satisfies(point -> {
            assertThat(point.actualPowerKw()).isEqualByComparingTo("2");
            assertThat(point.deliveredPowerKw()).isEqualByComparingTo("5.000");
            assertThat(point.errorKw()).isEqualByComparingTo("0.000");
        });
        assertThat(performance.achievementPercent()).isEqualByComparingTo("100.000");
        assertThat(count("event_inbox")).isEqualTo(1);
    }

    private ConsumerRecord<String, String> telemetry(
            UUID eventId,
            UUID deviceId,
            long sequence,
            Instant observedAt,
            String deviceType,
            String activePowerKw,
            boolean online,
            TelemetryQualityV1 quality
    ) throws Exception {
        var payload = new TelemetryNormalizedPayloadV1(
                SITE_ID, deviceId, sequence, observedAt, observedAt.plusSeconds(1),
                deviceType, new BigDecimal(activePowerKw), new BigDecimal("60"), online, quality
        );
        var event = new EventEnvelopeV1<>(
                eventId, EventTypes.TELEMETRY_NORMALIZED, 1, observedAt.plusSeconds(1),
                "telemetry-service", ORGANIZATION_ID, UUID.randomUUID(),
                UUID.randomUUID(), deviceId.toString(), payload
        );
        return new ConsumerRecord<>(
                EventTopics.TELEMETRY_NORMALIZED_V1, 0, sequence,
                deviceId.toString(), objectMapper.writeValueAsString(event)
        );
    }

    private void insertAllocation(
            UUID deviceId,
            String deviceType,
            String availablePowerKw,
            String allocatedPowerKw,
            String targetPowerKw
    ) {
        jdbcClient.sql("""
                INSERT INTO dispatch_allocations (
                    organization_id, dispatch_id, site_id, device_id, device_type,
                    source_available_power_kw, allocated_power_kw, expected_energy_kwh, score
                ) VALUES (
                    :organizationId, :dispatchId, :siteId, :deviceId, :deviceType,
                    :availablePowerKw, :allocatedPowerKw, 1, 1
                )
                """)
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", DISPATCH_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", deviceId)
                .param("deviceType", deviceType)
                .param("availablePowerKw", new BigDecimal(availablePowerKw))
                .param("allocatedPowerKw", new BigDecimal(allocatedPowerKw))
                .update();
        jdbcClient.sql("""
                INSERT INTO device_commands (
                    id, organization_id, dispatch_id, site_id, device_id,
                    command_type, target_power_kw, valid_from, expires_at,
                    acknowledgement_deadline_at, status, applied_power_kw,
                    requested_at, acknowledged_at
                ) VALUES (
                    :id, :organizationId, :dispatchId, :siteId, :deviceId,
                    'SET_POWER', :targetPowerKw, :startAt, :endAt,
                    :startAt, 'ACCEPTED', :targetPowerKw, :startAt, :startAt
                )
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", DISPATCH_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", deviceId)
                .param("targetPowerKw", new BigDecimal(targetPowerKw))
                .param("startAt", java.sql.Timestamp.from(START))
                .param("endAt", java.sql.Timestamp.from(START.plusSeconds(900)))
                .update();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }
}
