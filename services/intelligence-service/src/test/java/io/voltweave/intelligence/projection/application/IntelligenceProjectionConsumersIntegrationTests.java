package io.voltweave.intelligence.projection.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.portfolio.v1.PortfolioChangeTypeV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioLifecyclePayloadV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioResourceTypeV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.contracts.events.v1.TelemetryNormalizedPayloadV1;
import io.voltweave.contracts.events.v1.TelemetryQualityV1;
import io.voltweave.intelligence.PostgresTestConfiguration;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "voltweave.projection.enabled=true",
        "spring.kafka.listener.auto-startup=false"
})
@Import(PostgresTestConfiguration.class)
class IntelligenceProjectionConsumersIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.fromString(
            "10000000-0000-0000-0000-000000000019"
    );
    private static final UUID VPP_ID = UUID.fromString(
            "40000000-0000-0000-0000-000000000019"
    );
    private static final UUID SITE_ID = UUID.fromString(
            "20000000-0000-0000-0000-000000000019"
    );
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @Autowired
    private IntelligenceProjectionConsumers consumers;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearProjection() {
        jdbcClient.sql("""
                TRUNCATE energy_observations, vpp_site_projection, event_inbox
                """).update();
    }

    @Test
    void projectsMembershipLifecycleWithoutReplayOrTimeRegression() {
        UUID addEventId = UUID.randomUUID();
        var added = portfolioRecord(
                addEventId, EventTypes.VPP_SITE_ADDED,
                PortfolioChangeTypeV1.ADDED, NOW
        );
        consumers.consumePortfolio(added);
        consumers.consumePortfolio(added);

        assertEquals(1L, count("vpp_site_projection"));
        assertEquals(true, singleBoolean(
                "SELECT active FROM vpp_site_projection"
        ));
        assertEquals(1L, count("event_inbox"));

        consumers.consumePortfolio(portfolioRecord(
                UUID.randomUUID(), EventTypes.VPP_SITE_REMOVED,
                PortfolioChangeTypeV1.REMOVED, NOW.plusSeconds(60)
        ));
        consumers.consumePortfolio(portfolioRecord(
                UUID.randomUUID(), EventTypes.VPP_SITE_ADDED,
                PortfolioChangeTypeV1.ADDED, NOW.minusSeconds(60)
        ));

        assertEquals(false, singleBoolean(
                "SELECT active FROM vpp_site_projection"
        ));
    }

    @Test
    void projectsOnlyMeterAndSolarWithCanonicalSolarSign() {
        var meter = telemetryRecord(
                UUID.randomUUID(), UUID.randomUUID(), "SMART_METER",
                new BigDecimal("8.500"), 1
        );
        var solar = telemetryRecord(
                UUID.randomUUID(), UUID.randomUUID(), "SOLAR_INVERTER",
                new BigDecimal("-3.250"), 2
        );
        var battery = telemetryRecord(
                UUID.randomUUID(), UUID.randomUUID(), "BATTERY",
                new BigDecimal("-2.000"), 3
        );

        consumers.consumeTelemetry(meter);
        consumers.consumeTelemetry(meter);
        consumers.consumeTelemetry(solar);
        consumers.consumeTelemetry(battery);

        assertEquals(2L, count("energy_observations"));
        assertEquals(2L, count("event_inbox"));
        assertEquals(0, jdbcClient.sql("""
                SELECT power_kw FROM energy_observations
                WHERE energy_type = 'SOLAR_GENERATION'
                """).query(BigDecimal.class).single().compareTo(new BigDecimal("3.250")));
    }

    private ConsumerRecord<String, String> portfolioRecord(
            UUID eventId,
            String eventType,
            PortfolioChangeTypeV1 changeType,
            Instant occurredAt
    ) {
        var payload = new PortfolioLifecyclePayloadV1(
                SITE_ID, PortfolioResourceTypeV1.VPP_MEMBERSHIP, changeType, VPP_ID
        );
        var envelope = new EventEnvelopeV1<>(
                eventId, eventType, 1, occurredAt, "portfolio-service",
                ORGANIZATION_ID, UUID.randomUUID(), null, VPP_ID.toString(), payload
        );
        return record(EventTopics.PORTFOLIO_LIFECYCLE_V1, VPP_ID, envelope);
    }

    private ConsumerRecord<String, String> telemetryRecord(
            UUID eventId,
            UUID deviceId,
            String deviceType,
            BigDecimal power,
            long sequence
    ) {
        var payload = new TelemetryNormalizedPayloadV1(
                SITE_ID, deviceId, sequence, NOW.plusSeconds(sequence),
                NOW.plusSeconds(sequence + 1), deviceType, power, null,
                true, TelemetryQualityV1.VALID
        );
        var envelope = new EventEnvelopeV1<>(
                eventId, EventTypes.TELEMETRY_NORMALIZED, 1, NOW,
                "telemetry-service", ORGANIZATION_ID, UUID.randomUUID(),
                UUID.randomUUID(), deviceId.toString(), payload
        );
        return record(EventTopics.TELEMETRY_NORMALIZED_V1, deviceId, envelope);
    }

    private ConsumerRecord<String, String> record(
            String topic,
            UUID key,
            EventEnvelopeV1<?> envelope
    ) {
        try {
            return new ConsumerRecord<>(
                    topic, 0, 0, key.toString(), objectMapper.writeValueAsString(envelope)
            );
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table)
                .query(Long.class).single();
    }

    private boolean singleBoolean(String sql) {
        return jdbcClient.sql(sql).query(Boolean.class).single();
    }
}
