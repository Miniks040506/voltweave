package io.voltweave.dispatch.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.voltweave.dispatch.access.IntelligenceDispatchClient;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.Allocation;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.BaselinePoint;
import io.voltweave.dispatch.access.IntelligenceDispatchClient.DispatchInput;
import io.voltweave.dispatch.application.model.CreateDispatchCommand;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.domain.enums.DispatchStatus;
import io.voltweave.dispatch.persistence.DispatchRepository;
import io.voltweave.dispatch.persistence.DispatchRepository.IdempotencyRecord;

class DispatchApplicationServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-13T03:00:00Z");
    private static final Instant START = Instant.parse("2026-08-13T04:00:00Z");
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID VPP_ID = UUID.randomUUID();
    private static final UUID PREVIEW_ID = UUID.randomUUID();

    private DispatchRepository repository;
    private IntelligenceDispatchClient intelligenceClient;
    private DispatchApplicationService service;

    @BeforeEach
    void setUp() {
        repository = mock(DispatchRepository.class);
        intelligenceClient = mock(IntelligenceDispatchClient.class);
        service = new DispatchApplicationService(
                repository, intelligenceClient, Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void freezesDispatchablePreviewAndBaseline() {
        when(intelligenceClient.input(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, START, START.plus(Duration.ofMinutes(30))
        )).thenReturn(input(true));

        Dispatch result = service.create(command("dispatch-key"));

        assertEquals(DispatchStatus.SCHEDULED, result.status());
        assertEquals(new BigDecimal("1.500"), result.allocations().getFirst().expectedEnergyKwh());
        assertEquals(new BigDecimal("7"), result.allocations().getFirst().sourceAvailablePowerKw());
        assertEquals(2, result.baseline().points().size());
        var captor = ArgumentCaptor.forClass(Dispatch.class);
        verify(repository).insert(captor.capture(), any(), any());
        assertEquals(result, captor.getValue());
    }

    @Test
    void returnsExistingDispatchForMatchingIdempotencyRequest() {
        var existing = mock(Dispatch.class);
        var firstRepository = mock(DispatchRepository.class);
        var firstService = new DispatchApplicationService(
                firstRepository, intelligenceClient, Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(intelligenceClient.input(any(), any(), any(), any(), any())).thenReturn(input(true));
        Dispatch first = firstService.create(command("same-key"));
        var hash = ArgumentCaptor.forClass(String.class);
        verify(firstRepository).insert(any(), any(), hash.capture());
        clearInvocations(intelligenceClient);

        when(repository.findIdempotency(ORGANIZATION_ID, "same-key"))
                .thenReturn(Optional.of(new IdempotencyRecord(hash.getValue(), first.id())));
        when(repository.find(ORGANIZATION_ID, first.id())).thenReturn(Optional.of(existing));

        assertSame(existing, service.create(command("same-key")));
        verify(intelligenceClient, never()).input(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsUnsafeScheduleAndInfeasibleInput() {
        var past = new CreateDispatchCommand(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, "REDUCE_DEMAND", NOW.minusSeconds(1),
                Duration.ofMinutes(30), "operator", "key"
        );
        assertThrows(IllegalArgumentException.class, () -> service.create(past));

        when(intelligenceClient.input(any(), any(), any(), any(), any())).thenReturn(input(false));
        assertThrows(IllegalStateException.class, () -> service.create(command("key")));
    }

    @Test
    void rejectsInputForAnotherOptimizationInterval() {
        when(intelligenceClient.input(any(), any(), any(), any(), any()))
                .thenReturn(input(true, Duration.ofMinutes(45).toSeconds()));

        assertThrows(IllegalStateException.class, () -> service.create(command("interval-key")));
        verify(repository, never()).insert(any(), any(), any());
    }

    private static CreateDispatchCommand command(String key) {
        return new CreateDispatchCommand(
                ORGANIZATION_ID, VPP_ID, PREVIEW_ID, "REDUCE_DEMAND", START,
                Duration.ofMinutes(30), "operator-22", key
        );
    }

    private static DispatchInput input(boolean feasible) {
        return input(feasible, Duration.ofMinutes(30).toSeconds());
    }

    private static DispatchInput input(boolean feasible, long dispatchDurationSeconds) {
        return new DispatchInput(
                PREVIEW_ID, 2, ORGANIZATION_ID, VPP_ID,
                dispatchDurationSeconds,
                new BigDecimal("5"), new BigDecimal("6"), new BigDecimal("6"), feasible,
                UUID.randomUUID(), 3, "same-time-weighted-average", "1.0",
                NOW.plusSeconds(300),
                List.of(new Allocation(
                        UUID.randomUUID(), UUID.randomUUID(), "EV_CHARGER",
                        new BigDecimal("7"), new BigDecimal("3"), new BigDecimal("3"),
                        BigDecimal.ONE,
                        BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("0.8"), BigDecimal.ONE,
                        new BigDecimal("0.95"), new BigDecimal("3"), true
                )),
                List.of(
                        new BaselinePoint(START, new BigDecimal("10")),
                        new BaselinePoint(START.plusSeconds(900), new BigDecimal("11"))
                )
        );
    }
}
