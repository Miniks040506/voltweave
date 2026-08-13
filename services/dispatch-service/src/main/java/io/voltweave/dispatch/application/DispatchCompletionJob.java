package io.voltweave.dispatch.application;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.voltweave.dispatch.persistence.CompletionRepository;

@Service
@ConditionalOnProperty(
        prefix = "voltweave.completion", name = "enabled",
        havingValue = "true", matchIfMissing = true
)
public class DispatchCompletionJob {
    private static final int BATCH_SIZE = 50;
    private static final System.Logger LOGGER = System.getLogger(
            DispatchCompletionJob.class.getName()
    );

    private final CompletionRepository repository;
    private final DispatchCompletionApplicationService service;
    private final Clock clock = Clock.systemUTC();

    public DispatchCompletionJob(
            CompletionRepository repository,
            DispatchCompletionApplicationService service
    ) {
        this.repository = repository;
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${voltweave.completion.poll-delay:1s}")
    public void completeDueDispatches() {
        var now = clock.instant();
        for (var dispatchId : repository.dueDispatchIds(now, BATCH_SIZE)) {
            try {
                service.complete(dispatchId, now);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Cannot complete dispatch " + dispatchId, exception);
            }
        }
    }
}
