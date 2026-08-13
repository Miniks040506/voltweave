package io.voltweave.settlement.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
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
import io.voltweave.settlement.access.PortfolioAccessClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(PostgresTestConfiguration.class)
@AutoConfigureMockMvc
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

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DispatchSettlementClient dispatchClient;

    @MockitoBean
    private PortfolioAccessClient portfolioAccessClient;

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

    @Test
    void servesBothReadRoutesAndEnforcesVppOwnership() throws Exception {
        when(dispatchClient.input(DISPATCH_ID)).thenReturn(input());
        consumer.consume(completionRecord());
        UUID settlementId = jdbcClient.sql("SELECT id FROM settlements")
                .query(UUID.class).single();
        when(portfolioAccessClient.requireVppAccess("operator", VPP_ID))
                .thenReturn(ORGANIZATION_ID);

        mockMvc.perform(get("/api/v1/settlements/{id}", settlementId)
                        .with(operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchId").value(DISPATCH_ID.toString()))
                .andExpect(jsonPath("$.lines.length()").value(2));
        mockMvc.perform(get("/api/v1/dispatches/{id}/settlements", DISPATCH_ID)
                        .with(operatorJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(settlementId.toString()));

        when(portfolioAccessClient.requireVppAccess("operator", VPP_ID))
                .thenReturn(UUID.randomUUID());
        mockMvc.perform(get("/api/v1/settlements/{id}", settlementId)
                        .with(operatorJwt()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/settlements/{id}", settlementId))
                .andExpect(status().isUnauthorized());
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

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operatorJwt() {
        return jwt().jwt(token -> token.subject("operator"))
                .authorities(new SimpleGrantedAuthority("ROLE_VPP_OPERATOR"));
    }
}
