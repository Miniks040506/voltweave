package io.voltweave.dispatch.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.PortfolioAccessClient;

@Component
public class AutomationEvaluationJob {
    private static final System.Logger LOGGER = System.getLogger(
            AutomationEvaluationJob.class.getName()
    );

    private final PortfolioAccessClient portfolioClient;
    private final IntelligenceDispatchClient intelligenceClient;
    private final AutomationApplicationService automationService;

    public AutomationEvaluationJob(
            PortfolioAccessClient portfolioClient,
            IntelligenceDispatchClient intelligenceClient,
            AutomationApplicationService automationService
    ) {
        this.portfolioClient = portfolioClient;
        this.intelligenceClient = intelligenceClient;
        this.automationService = automationService;
    }

    @Scheduled(fixedDelayString = "${voltweave.automation.evaluation-delay:1m}")
    public void evaluate() {
        automationService.expireCandidates();
        for (var policy : portfolioClient.activeAutomationPolicies()) {
            try {
                if (!"PEAK_LIMIT".equals(policy.triggerType())) {
                    continue;
                }
                intelligenceClient.automationPlan(policy)
                        .flatMap(plan -> automationService.createCandidate(policy, plan));
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Automation evaluation failed for policy " + policy.id(), exception);
            }
        }
    }
}
