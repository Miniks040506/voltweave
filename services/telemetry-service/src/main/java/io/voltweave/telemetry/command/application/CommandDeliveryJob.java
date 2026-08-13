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
                repository.markPublished(
                        command.commandId(), clock.instant(),
                        clock.instant().plus(retryDelay(command.attempts()), ChronoUnit.SECONDS)
                );
            } catch (Exception exception) {
                repository.markFailed(
                        command.commandId(),
                        clock.instant().plus(
                                retryDelay(command.attempts()), ChronoUnit.SECONDS
                        ),
                        exception.getMessage()
                );
            }
        }
    }

    private static long retryDelay(int attempts) {
        return Math.min(30, 1L << Math.min(attempts, 5));
    }
}
