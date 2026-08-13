package io.voltweave.telemetry.command.application;

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

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.command.v1.CommandRequestedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.telemetry.TimescaleTestConfiguration;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "voltweave.ingress.enabled=false",
        "voltweave.processing.enabled=false",
        "voltweave.command.enabled=true",
        "spring.kafka.listener.auto-startup=false"
})
@Import(TimescaleTestConfiguration.class)
class CommandRequestedConsumerIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DISPATCH_ID = UUID.randomUUID();
    private static final UUID SITE_ID = UUID.randomUUID();
    private static final UUID DEVICE_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final Instant VALID_FROM = Instant.parse("2026-08-13T10:00:00Z");

    @Autowired
    private CommandRequestedConsumer consumer;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void clearState() {
        jdbcClient.sql("TRUNCATE command_deliveries, event_inbox").update();
    }

    @Test
    void storesOneDurableMqttDeliveryWhenKafkaEventIsReplayed() throws Exception {
        UUID eventId = UUID.randomUUID();
        ConsumerRecord<String, String> record = record(eventId);

        consumer.consume(record);
        consumer.consume(record);

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("command_deliveries")).isEqualTo(1);
        var delivery = jdbcClient.sql("""
                SELECT mqtt_topic, mqtt_payload::text, correlation_id, requested_event_id
                FROM command_deliveries WHERE command_id = :commandId
                """)
                .param("commandId", commandId())
                .query((row, number) -> new Delivery(
                        row.getString("mqtt_topic"), row.getString("mqtt_payload"),
                        row.getObject("correlation_id", UUID.class),
                        row.getObject("requested_event_id", UUID.class)
                )).single();
        assertThat(delivery.topic()).isEqualTo(
                "voltweave/%s/%s/%s/command".formatted(
                        ORGANIZATION_ID, SITE_ID, DEVICE_ID
                )
        );
        assertThat(objectMapper.readTree(delivery.payload()).path("targetPowerKw").decimalValue())
                .isEqualByComparingTo("-12.500");
        assertThat(delivery.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(delivery.requestedEventId()).isEqualTo(eventId);
    }

    @Test
    void ignoresAcknowledgementEventsOnTheSharedLifecycleTopic() throws Exception {
        var event = EventEnvelopeV1.create(
                EventTypes.COMMAND_ACKNOWLEDGED, "telemetry-service", ORGANIZATION_ID,
                CORRELATION_ID, UUID.randomUUID(), DEVICE_ID.toString(),
                objectMapper.createObjectNode(), VALID_FROM
        );
        consumer.consume(new ConsumerRecord<>(
                EventTopics.COMMAND_LIFECYCLE_V1, 0, 1,
                DEVICE_ID.toString(), objectMapper.writeValueAsString(event)
        ));

        assertThat(count("event_inbox")).isZero();
        assertThat(count("command_deliveries")).isZero();
    }

    private ConsumerRecord<String, String> record(UUID eventId) throws Exception {
        var payload = new CommandRequestedPayloadV1(
                commandId(), DISPATCH_ID, SITE_ID, DEVICE_ID, "SET_POWER",
                new BigDecimal("-12.500"), VALID_FROM,
                VALID_FROM.plusSeconds(900), null
        );
        var event = new EventEnvelopeV1<>(
                eventId, EventTypes.COMMAND_REQUESTED, 1, VALID_FROM.minusSeconds(60),
                "dispatch-service", ORGANIZATION_ID, CORRELATION_ID, UUID.randomUUID(),
                DEVICE_ID.toString(), payload
        );
        return new ConsumerRecord<>(
                EventTopics.COMMAND_LIFECYCLE_V1, 0, 0,
                DEVICE_ID.toString(), objectMapper.writeValueAsString(event)
        );
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private static UUID commandId() {
        return UUID.fromString("40000000-0000-0000-0000-000000000001");
    }

    private record Delivery(
            String topic,
            String payload,
            UUID correlationId,
            UUID requestedEventId
    ) {
    }
}
