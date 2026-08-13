package io.voltweave.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.dispatch.v1.DispatchCompletedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.settlement.PostgresTestConfiguration;
import io.voltweave.settlement.access.DispatchSettlementClient;
import io.voltweave.settlement.access.DispatchSettlementClient.BaselinePoint;
import io.voltweave.settlement.access.DispatchSettlementClient.Participant;
import io.voltweave.settlement.access.DispatchSettlementClient.SettlementInput;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresTestConfiguration.class)
@Transactional
class SettlementWorkflowIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DISPATCH_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final UUID BASELINE_ID = UUID.randomUUID();
    private static final Instant END = Instant.parse("2026-08-13T03:00:00Z");

    @Autowired
    private DispatchCompletedConsumer consumer;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DispatchSettlementClient dispatchClient;

    @Test
    void copiesAndCalculatesSettlementExactlyOnceWhenKafkaReplays() throws Exception {
        when(dispatchClient.input(DISPATCH_ID)).thenReturn(input());
        ConsumerRecord<String, String> record = completionRecord();

        consumer.consume(record);
        consumer.consume(record);

        assertThat(count("event_inbox")).isEqualTo(1);
        assertThat(count("settlements")).isEqualTo(1);
        assertThat(count("settlement_baseline_points")).isEqualTo(1);
        assertThat(count("settlement_lines")).isEqualTo(2);
        assertThat(decimal("expected_energy_kwh", "settlements"))
                .isEqualByComparingTo("10.000000");
        assertThat(decimal("delivered_energy_kwh", "settlements"))
                .isEqualByComparingTo("9.500000");
        assertThat(decimal("achievement_percent", "settlements"))
                .isEqualByComparingTo("95.000");
    }

    private ConsumerRecord<String, String> completionRecord() throws Exception {
        var payload = new DispatchCompletedPayloadV1(
                DISPATCH_ID, VPP_ID, "PARTIALLY_COMPLETED", new BigDecimal("10"),
                new BigDecimal("9.5"), BASELINE_ID, 2,
                END.minusSeconds(3_600), END, END.plusSeconds(1)
        );
        var event = EventEnvelopeV1.create(
                EventTypes.DISPATCH_COMPLETED, "dispatch-service", ORGANIZATION_ID,
                DISPATCH_ID, null, DISPATCH_ID.toString(), payload, END.plusSeconds(1)
        );
        return new ConsumerRecord<>(
                EventTopics.DISPATCH_LIFECYCLE_V1, 0, 0,
                DISPATCH_ID.toString(), objectMapper.writeValueAsString(event)
        );
    }

    private static SettlementInput input() {
        return new SettlementInput(
                ORGANIZATION_ID, DISPATCH_ID, VPP_ID, "PARTIALLY_COMPLETED",
                new BigDecimal("10"), END.minusSeconds(3_600), END,
                END.minusSeconds(7_200), BASELINE_ID, 2,
                "persistence", "v1",
                List.of(new BaselinePoint(END, new BigDecimal("12"))),
                List.of(
                        new Participant(
                                UUID.randomUUID(), UUID.randomUUID(), "BATTERY",
                                new BigDecimal("6"), new BigDecimal("6"),
                                new BigDecimal("5.5")
                        ),
                        new Participant(
                                UUID.randomUUID(), UUID.randomUUID(), "EV_CHARGER",
                                new BigDecimal("4"), new BigDecimal("4"),
                                new BigDecimal("4")
                        )
                )
        );
    }

    private long count(String table) {
        return jdbcClient.sql("SELECT count(*) FROM " + table).query(Long.class).single();
    }

    private BigDecimal decimal(String column, String table) {
        return jdbcClient.sql("SELECT " + column + " FROM " + table)
                .query(BigDecimal.class).single();
    }
}
