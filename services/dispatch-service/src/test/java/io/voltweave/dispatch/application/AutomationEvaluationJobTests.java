package io.voltweave.dispatch.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.PortfolioAccessClient;
import io.voltweave.dispatch.application.model.AutomationPlan;
import io.voltweave.dispatch.application.model.AutomationPolicy;

class AutomationEvaluationJobTests {
    @Test
    void evaluatesPricePolicyAndSkipsManualPolicy() {
        var portfolio = mock(PortfolioAccessClient.class);
        var intelligence = mock(IntelligenceDispatchClient.class);
        var automation = mock(AutomationApplicationService.class);
        var price = policy("PRICE_THRESHOLD");
        var manual = policy("MANUAL");
        var plan = new AutomationPlan(
                Instant.parse("2026-08-13T17:00:00Z"), Duration.ofMinutes(30),
                UUID.randomUUID(), true
        );
        when(portfolio.activeAutomationPolicies()).thenReturn(List.of(price, manual));
        when(intelligence.automationPlan(price)).thenReturn(Optional.of(plan));

        new AutomationEvaluationJob(portfolio, intelligence, automation).evaluate();

        verify(automation).expireCandidates();
        verify(automation).createCandidate(price, plan);
        verify(intelligence, never()).automationPlan(manual);
    }

    private static AutomationPolicy policy(String trigger) {
        return new AutomationPolicy(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), trigger,
                "AUTO_DISPATCH", "PEAK_LIMIT".equals(trigger) ? BigDecimal.TEN : null,
                "PRICE_THRESHOLD".equals(trigger) ? new BigDecimal("0.20") : null,
                10, new BigDecimal("5"), 30, 2
        );
    }
}
