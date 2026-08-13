package io.voltweave.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.voltweave.dispatch.application.model.DeviceCommand;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.domain.enums.CommandStatus;
import io.voltweave.dispatch.domain.enums.DispatchStatus;
import io.voltweave.dispatch.persistence.CommandRepository;
import io.voltweave.dispatch.persistence.DispatchRepository;
import tools.jackson.databind.json.JsonMapper;

class CommandApplicationServiceTests {
    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DISPATCH_ID = UUID.randomUUID();
    private static final UUID CORRELATION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-08-13T04:00:00Z");

    private final CommandRepository commandRepository = mock(CommandRepository.class);
    private final DispatchRepository dispatchRepository = mock(DispatchRepository.class);
    private final CommandApplicationService service = new CommandApplicationService(
            commandRepository, dispatchRepository,
            JsonMapper.builder().findAndAddModules().build(),
            Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void preparesBatteryAndEvCommandsWithAtomicOutboxEvents() {
        when(dispatchRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(dispatch()));
        when(commandRepository.findByDispatch(ORGANIZATION_ID, DISPATCH_ID))
                .thenReturn(List.of());

        List<DeviceCommand> result = service.prepare(DISPATCH_ID, CORRELATION_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DeviceCommand::targetPowerKw)
                .containsExactlyInAnyOrder(new BigDecimal("-4.000"), new BigDecimal("2.000"));
        var command = ArgumentCaptor.forClass(DeviceCommand.class);
        var json = ArgumentCaptor.forClass(String.class);
        verify(commandRepository, org.mockito.Mockito.times(2)).insert(
                command.capture(), any(), anyString(), json.capture()
        );
        assertThat(command.getAllValues()).allMatch(value ->
                value.status() == CommandStatus.REQUESTED
                        && value.validFrom().equals(NOW.plusSeconds(900))
        );
        assertThat(json.getAllValues()).allMatch(value ->
                value.contains("\"eventType\":\"CommandRequested\"")
                        && value.contains(CORRELATION_ID.toString())
        );
        verify(commandRepository).transitionDispatch(
                ORGANIZATION_ID, DISPATCH_ID, DispatchStatus.SCHEDULED,
                DispatchStatus.PREPARING, 0
        );
    }

    @Test
    void replayReturnsExistingCommandsWithoutCreatingAnotherEvent() {
        var dispatch = dispatch();
        var existing = new DeviceCommand(
                UUID.randomUUID(), ORGANIZATION_ID, DISPATCH_ID,
                UUID.randomUUID(), UUID.randomUUID(), "SET_POWER",
                new BigDecimal("-4"), dispatch.scheduledStartAt(), dispatch.scheduledEndAt(),
                CommandStatus.REQUESTED, null, null, NOW, null, 0
        );
        when(dispatchRepository.findById(DISPATCH_ID)).thenReturn(Optional.of(dispatch));
        when(commandRepository.findByDispatch(ORGANIZATION_ID, DISPATCH_ID))
                .thenReturn(List.of(existing));

        assertThat(service.prepare(DISPATCH_ID, CORRELATION_ID)).containsExactly(existing);

        verify(commandRepository, never()).insert(any(), any(), anyString(), anyString());
        verify(commandRepository, never()).transitionDispatch(
                any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyLong()
        );
    }

    private static Dispatch dispatch() {
        var battery = new Dispatch.Allocation(
                UUID.randomUUID(), UUID.randomUUID(), "BATTERY",
                new BigDecimal("5"), new BigDecimal("4"),
                new BigDecimal("1"), new BigDecimal("0.9")
        );
        var ev = new Dispatch.Allocation(
                UUID.randomUUID(), UUID.randomUUID(), "EV_CHARGER",
                new BigDecimal("7"), new BigDecimal("5"),
                new BigDecimal("1.25"), new BigDecimal("0.8")
        );
        var baseline = new Dispatch.Baseline(
                UUID.randomUUID(), 1, "model", "1", NOW.plusSeconds(3600), NOW, List.of()
        );
        return new Dispatch(
                DISPATCH_ID, ORGANIZATION_ID, UUID.randomUUID(), UUID.randomUUID(), 1,
                "REDUCE_DEMAND", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
                NOW.plusSeconds(900), NOW.plusSeconds(2700), DispatchStatus.SCHEDULED,
                "operator", NOW, 0, baseline, List.of(battery, ev)
        );
    }
}
