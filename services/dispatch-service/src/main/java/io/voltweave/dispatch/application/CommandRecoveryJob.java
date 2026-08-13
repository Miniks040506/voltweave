package io.voltweave.dispatch.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.dispatch.persistence.CommandRepository;

@Service
public class CommandRecoveryJob {
    private static final int BATCH_SIZE = 50;

    private final CommandRepository repository;
    private final Clock clock;

    @Autowired
    public CommandRecoveryJob(CommandRepository repository) {
        this(repository, Clock.systemUTC());
    }

    CommandRecoveryJob(CommandRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${voltweave.command.recovery-poll-delay:1s}")
    @Transactional
    public void recoverStalled() {
        var now = clock.instant();
        for (var command : repository.lockStalledCommands(now, BATCH_SIZE)) {
            repository.timeOutUnacknowledged(command.commandId(), now);
            repository.failPreparingDispatch(
                    command.organizationId(), command.dispatchId()
            );
        }
    }
}
