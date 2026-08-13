package io.voltweave.dispatch.application;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.voltweave.dispatch.application.model.AutomationPlan;
import io.voltweave.dispatch.application.model.AutomationPolicy;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.persistence.AutomationRepository;

class AutomationApplicationServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-13T03:00:00Z");
    private AutomationRepository repository;
    private DispatchApplicationService dispatchService;
    private CommandApplicationService commandService;
    private AutomationApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(AutomationRepository.class);
        dispatchService = mock(DispatchApplicationService.class);
        commandService = mock(CommandApplicationService.class);
        service = new AutomationApplicationService(
                repository, dispatchService, commandService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void preparesCommandsOnlyForAutoDispatch() {
        var dispatch = mock(Dispatch.class);
        UUID dispatchId = UUID.randomUUID();
        when(dispatch.id()).thenReturn(dispatchId);
        when(dispatchService.create(any())).thenReturn(dispatch);

        assertSame(dispatch, service.createCandidate(policy("AUTO_DISPATCH"), plan(true))
                .orElseThrow());

        verify(repository).insert(any(), any(), any(), any());
        verify(commandService).prepare(any(), any());
    }

    @Test
    void leavesOperatorCandidateScheduledAndReplaysExistingRun() {
        var dispatch = mock(Dispatch.class);
        when(dispatchService.create(any())).thenReturn(dispatch);
        assertSame(dispatch, service.createCandidate(
                policy("REQUIRE_OPERATOR"), plan(true)
        ).orElseThrow());
        verify(commandService, never()).prepare(any(), any());

        var duplicateRepository = mock(AutomationRepository.class);
        UUID dispatchId = UUID.randomUUID();
        when(duplicateRepository.findDispatch(any(), anyInt(), any()))
                .thenReturn(Optional.of(dispatchId));
        when(dispatchService.find(dispatchId)).thenReturn(Optional.of(dispatch));
        var duplicateService = new AutomationApplicationService(
                duplicateRepository, dispatchService, commandService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        assertSame(dispatch, duplicateService.createCandidate(
                policy("AUTO_DISPATCH"), plan(true)
        ).orElseThrow());
    }

    @Test
    void rejectsInfeasiblePlanWithoutWriting() {
        assertTrue(service.createCandidate(policy("AUTO_DISPATCH"), plan(false)).isEmpty());
        verify(repository, never()).insert(any(), any(), any(), any());
        verify(dispatchService, never()).create(any());
    }

    private static AutomationPolicy policy(String approvalMode) {
        return new AutomationPolicy(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "PEAK_LIMIT",
                approvalMode, new BigDecimal("10"), null, 10, new BigDecimal("5"), 30, 2
        );
    }

    private static AutomationPlan plan(boolean feasible) {
        return new AutomationPlan(
                NOW.plusSeconds(900), Duration.ofMinutes(30), UUID.randomUUID(), feasible
        );
    }
}
