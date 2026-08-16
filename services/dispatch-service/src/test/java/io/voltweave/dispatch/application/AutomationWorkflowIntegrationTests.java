package io.voltweave.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.dispatch.PostgresTestConfiguration;
import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.Allocation;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.BaselinePoint;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.DispatchInput;
import io.voltweave.dispatch.application.model.AutomationPlan;
import io.voltweave.dispatch.application.model.AutomationPolicy;

@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "voltweave.automation.evaluation-delay=1h"
})
@Import(PostgresTestConfiguration.class)
@Transactional
class AutomationWorkflowIntegrationTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final UUID PREVIEW_ID = UUID.randomUUID();

    @Autowired
    private AutomationApplicationService automationService;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean
    private IntelligenceDispatchClient intelligenceClient;

    @Test
    void autoDispatchPersistsOneRunAndCommandOnReplay() {
        Instant startAt = nextQuarterHour();
        when(intelligenceClient.input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, startAt, startAt.plusSeconds(1800)
        )).thenReturn(input(startAt));
        var policy = policy("AUTO_DISPATCH");
        var plan = new AutomationPlan(
                startAt, Duration.ofMinutes(30), PREVIEW_ID, true
        );

        UUID firstId = automationService.createCandidate(policy, plan).orElseThrow().id();
        UUID replayId = automationService.createCandidate(policy, plan).orElseThrow().id();

        assertThat(replayId).isEqualTo(firstId);
        assertCount("automation_runs", 1);
        assertCount("dispatches", 1);
        assertCount("device_reservations", 1);
        assertCount("device_commands", 1);
        assertCount("event_outbox", 1);
        assertThat(jdbcClient.sql("SELECT status FROM dispatches WHERE id = :id")
                .param("id", firstId).query(String.class).single()).isEqualTo("PREPARING");
    }

    private void assertCount(String table, int expected) {
        assertThat(jdbcClient.sql("SELECT count(*) FROM " + table)
                .query(Integer.class).single()).isEqualTo(expected);
    }

    private static AutomationPolicy policy(String approvalMode) {
        return new AutomationPolicy(
                UUID.randomUUID(), ORGANIZATION_ID, VPP_ID, "PEAK_LIMIT", approvalMode,
                new BigDecimal("50"), null, 10, new BigDecimal("10"), 30, 2
        );
    }

    private static DispatchInput input(Instant startAt) {
        return new DispatchInput(
                PREVIEW_ID, 1, ORGANIZATION_ID, VPP_ID,
                Duration.ofMinutes(30).toSeconds(),
                new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("11"), true,
                UUID.randomUUID(), 3, "persistence-v1", "1.0", startAt.plusSeconds(3600),
                List.of(new Allocation(
                        UUID.randomUUID(), UUID.randomUUID(), "BATTERY",
                        new BigDecimal("12"), new BigDecimal("6"), new BigDecimal("0.9"),
                        new BigDecimal("0.8"), BigDecimal.ONE, new BigDecimal("0.9"),
                        BigDecimal.ONE, new BigDecimal("0.92"), new BigDecimal("11"), true
                )),
                List.of(
                        new BaselinePoint(startAt, new BigDecimal("52")),
                        new BaselinePoint(startAt.plusSeconds(900), new BigDecimal("50"))
                )
        );
    }

    private static Instant nextQuarterHour() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        long minutes = now.getEpochSecond() / 60;
        return Instant.ofEpochSecond(((minutes / 15) + 2) * 15 * 60);
    }
}
