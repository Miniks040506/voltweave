package io.voltweave.telemetry.command.application;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.telemetry.command.persistence.CommandGatewayRepository;
import io.voltweave.telemetry.ingress.MqttTelemetryIngress;

@Service
@ConditionalOnProperty(prefix = "voltweave.command", name = "enabled", havingValue = "true")
@ConditionalOnBean(MqttTelemetryIngress.class)
public class CommandDeliveryJob {
    private static final int BATCH_SIZE = 50;

    private final CommandGatewayRepository repository;
    private final MqttTelemetryIngress mqtt;
    private final Clock clock;

    @Autowired
    public CommandDeliveryJob(CommandGatewayRepository repository, MqttTelemetryIngress mqtt) {
        this(repository, mqtt, Clock.systemUTC());
    }

    CommandDeliveryJob(
            CommandGatewayRepository repository,
            MqttTelemetryIngress mqtt,
            Clock clock
    ) {
        this.repository = repository;
        this.mqtt = mqtt;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${voltweave.command.poll-delay:1s}")
    @Transactional
    public void publishReady() {
        for (var command : repository.lockReady(clock.instant(), BATCH_SIZE)) {
            try {
                mqtt.publishCommand(command.mqttTopic(), command.mqttPayload());
                repository.markPublished(command.commandId(), clock.instant());
            } catch (Exception exception) {
                long delay = Math.min(60, 1L << Math.min(command.attempts(), 6));
                repository.markFailed(
                        command.commandId(),
                        clock.instant().plus(delay, ChronoUnit.SECONDS),
                        exception.getMessage()
                );
            }
        }
    }
}
