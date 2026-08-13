package io.voltweave.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.command.v1.CommandAcknowledgedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.dispatch.PostgresTestConfiguration;
import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.Allocation;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.ReplacementPlan;
import io.voltweave.dispatch.access.PortfolioAccessClient;
import io.voltweave.dispatch.access.PortfolioAccessClient.RecoveryPolicy;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "voltweave.performance.recovery-poll-delay=1h",
        "voltweave.performance.stale-after=1m"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class UnderDeliveryRecoveryIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final UUID DISPATCH_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID ORIGINAL_DEVICE_ID = UUID.randomUUID();
    private static final UUID REPLACEMENT_DEVICE_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Autowired
    private RebalanceApplicationService service;

    @Autowired
    private CommandAcknowledgedConsumer acknowledgementConsumer;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PortfolioAccessClient portfolioClient;

    @MockitoBean
    private IntelligenceDispatchClient intelligenceClient;

    @BeforeEach
    void seedActiveUnderDeliveringDispatch() {
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
                .param("vppId", VPP_ID)
                .param("previewId", UUID.randomUUID())
                .param("startAt", timestamp(NOW.minusSeconds(60)))
                .param("endAt", timestamp(NOW.plusSeconds(900)))
                .param("createdAt", timestamp(NOW.minusSeconds(120)))
                .update();
        insertOriginalAllocationAndCommand();
        insertPerformancePoint();
        when(portfolioClient.recoveryPolicy(ORGANIZATION_ID, VPP_ID))
                .thenReturn(new RecoveryPolicy(0, 10, 30, 60));
    }

    @Test
    void waitsForGraceThenCommandsReplacementAndReturnsActiveAfterAck() throws Exception {
        UUID replacementPreview = UUID.randomUUID();
        when(intelligenceClient.replacementPlan(
                any(), any(), any(), any(Integer.class), any(Duration.class), any()
        )).thenReturn(new ReplacementPlan(
                replacementPreview, new BigDecimal("6"), true,
                List.of(replacementCandidate())
        ));

        service.evaluate(DISPATCH_ID, NOW);
        service.evaluate(DISPATCH_ID, NOW.plusSeconds(29));

        assertThat(value("status")).isEqualTo("ACTIVE");
        assertThat(count("dispatch_rebalances")).isZero();

        service.evaluate(DISPATCH_ID, NOW.plusSeconds(31));

        assertThat(value("status")).isEqualTo("REBALANCING");
        assertThat(count("dispatch_rebalances")).isEqualTo(1);
        assertThat(count("dispatch_replacement_allocations")).isEqualTo(1);
        assertThat(count("device_reservations")).isEqualTo(1);
        assertThat(count("event_outbox")).isEqualTo(1);
        UUID commandId = jdbcClient.sql("""
                SELECT id FROM device_commands WHERE device_id = :deviceId
                """).param("deviceId", REPLACEMENT_DEVICE_ID).query(UUID.class).single();
        assertThat(jdbcClient.sql("""
                SELECT target_power_kw FROM device_commands WHERE id = :id
                """).param("id", commandId).query(BigDecimal.class).single())
                .isEqualByComparingTo("-6.000");

        acknowledgementConsumer.consume(acknowledgement(commandId));

        assertThat(value("status")).isEqualTo("ACTIVE");
        service.evaluate(DISPATCH_ID, NOW.plusSeconds(80));
        verify(intelligenceClient).replacementPlan(
                any(), any(), any(), any(Integer.class), any(Duration.class), any()
        );
    }

    @Test
    void failsSafelyWhenNoReplacementCanMeetTheGap() {
        when(intelligenceClient.replacementPlan(
                any(), any(), any(), any(Integer.class), any(Duration.class), any()
        )).thenReturn(new ReplacementPlan(
                UUID.randomUUID(), new BigDecimal("2"), false, List.of()
        ));

        service.evaluate(DISPATCH_ID, NOW);
        service.evaluate(DISPATCH_ID, NOW.plusSeconds(31));

        assertThat(value("status")).isEqualTo("FAILED");
        assertThat(jdbcClient.sql("SELECT status FROM dispatch_rebalances")
                .query(String.class).single()).isEqualTo("FAILED");
        assertThat(count("device_commands")).isEqualTo(1);
        verify(intelligenceClient, times(1)).replacementPlan(
                any(), any(), any(), any(Integer.class), any(Duration.class), any()
        );
    }

    private void insertOriginalAllocationAndCommand() {
        jdbcClient.sql("""
                INSERT INTO dispatch_allocations (
                    organization_id, dispatch_id, site_id, device_id, device_type,
                    source_available_power_kw, allocated_power_kw, expected_energy_kwh, score
                ) VALUES (:organizationId, :dispatchId, :siteId, :deviceId,
                    'BATTERY', 10, 10, 2.5, 1)
                """)
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", DISPATCH_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", ORIGINAL_DEVICE_ID)
                .update();
        jdbcClient.sql("""
                INSERT INTO device_commands (
                    id, organization_id, dispatch_id, site_id, device_id,
                    command_type, target_power_kw, valid_from,
                    acknowledgement_deadline_at, expires_at, status,
                    applied_power_kw, requested_at, acknowledged_at
                ) VALUES (:id, :organizationId, :dispatchId, :siteId, :deviceId,
                    'SET_POWER', -10, :startAt, :startAt, :endAt,
                    'ACCEPTED', -10, :startAt, :startAt)
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", DISPATCH_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", ORIGINAL_DEVICE_ID)
                .param("startAt", timestamp(NOW.minusSeconds(60)))
                .param("endAt", timestamp(NOW.plusSeconds(900)))
                .update();
    }

    private void insertPerformancePoint() {
        jdbcClient.sql("""
                INSERT INTO dispatch_performance_points (
                    id, organization_id, dispatch_id, site_id, device_id,
                    telemetry_event_id, observed_at, target_power_kw,
                    requested_power_kw, actual_power_kw, delivered_power_kw,
                    error_kw, error_percent, cumulative_delivered_energy_kwh,
                    online, quality, recorded_at
                ) VALUES (:id, :organizationId, :dispatchId, :siteId, :deviceId,
                    :eventId, :observedAt, -10, 10, -4, 4, 6, 60, 0,
                    true, 'VALID', :recordedAt)
                """)
                .param("id", UUID.randomUUID())
                .param("organizationId", ORGANIZATION_ID)
                .param("dispatchId", DISPATCH_ID)
                .param("siteId", SITE_ID)
                .param("deviceId", ORIGINAL_DEVICE_ID)
                .param("eventId", UUID.randomUUID())
                .param("observedAt", timestamp(NOW))
                .param("recordedAt", timestamp(NOW))
                .update();
    }

    private Allocation replacementCandidate() {
        return new Allocation(
                SITE_ID, REPLACEMENT_DEVICE_ID, "BATTERY",
                new BigDecimal("8"), new BigDecimal("4"), BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("0.9"), new BigDecimal("6"), true
        );
    }

    private ConsumerRecord<String, String> acknowledgement(UUID commandId) throws Exception {
        var payload = new CommandAcknowledgedPayloadV1(
                commandId, DISPATCH_ID, SITE_ID, REPLACEMENT_DEVICE_ID,
                "ACCEPTED", new BigDecimal("-6"), null, NOW.plusSeconds(32)
        );
        var event = EventEnvelopeV1.create(
                EventTypes.COMMAND_ACKNOWLEDGED, "telemetry-service", ORGANIZATION_ID,
                UUID.randomUUID(), UUID.randomUUID(), REPLACEMENT_DEVICE_ID.toString(),
                payload, NOW.plusSeconds(32)
        );
        return new ConsumerRecord<>(
                EventTopics.COMMAND_LIFECYCLE_V1, 0, 0,
                REPLACEMENT_DEVICE_ID.toString(), objectMapper.writeValueAsString(event)
        );
    }

    private String value(String column) {
        return jdbcClient.sql("SELECT " + column + " FROM dispatches WHERE id = :id")
                .param("id", DISPATCH_ID).query(String.class).single();
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}
