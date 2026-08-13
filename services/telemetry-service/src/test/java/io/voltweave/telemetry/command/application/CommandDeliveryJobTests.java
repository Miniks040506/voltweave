package io.voltweave.telemetry.command.application;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.Test;

import io.voltweave.telemetry.command.application.model.CommandDelivery;
import io.voltweave.telemetry.command.persistence.CommandGatewayRepository;
import io.voltweave.telemetry.ingress.MqttTelemetryIngress;

class CommandDeliveryJobTests {
    private static final Instant NOW = Instant.parse("2026-08-13T10:00:00Z");
    private static final UUID COMMAND_ID = UUID.randomUUID();

    private final CommandGatewayRepository repository = mock(CommandGatewayRepository.class);
    private final MqttTelemetryIngress mqtt = mock(MqttTelemetryIngress.class);
    private final CommandDeliveryJob job = new CommandDeliveryJob(
            repository, mqtt, Clock.fixed(NOW, ZoneOffset.UTC)
    );

    @Test
    void marksCommandPublishedAfterMqttAcceptsIt() throws Exception {
        when(repository.lockReady(NOW, 50)).thenReturn(List.of(delivery(0)));

        job.publishReady();

        verify(mqtt).publishCommand("device/command", "{}");
        verify(repository).markPublished(COMMAND_ID, NOW, NOW.plusSeconds(1));
    }

    @Test
    void schedulesExponentialRetryWhenMqttIsUnavailable() throws Exception {
        when(repository.lockReady(NOW, 50)).thenReturn(List.of(delivery(3)));
        doThrow(new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED))
                .when(mqtt).publishCommand("device/command", "{}");

        job.publishReady();

        verify(repository).markFailed(
                COMMAND_ID, NOW.plusSeconds(8), "Client is not connected"
        );
    }

    private static CommandDelivery delivery(int attempts) {
        return new CommandDelivery(
                COMMAND_ID, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "device/command", "{}", NOW.minusSeconds(1),
                NOW.plusSeconds(30), NOW.plusSeconds(60), attempts
        );
    }
}
