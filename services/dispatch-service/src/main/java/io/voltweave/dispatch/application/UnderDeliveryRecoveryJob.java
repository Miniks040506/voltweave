package io.voltweave.dispatch.application;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import io.voltweave.dispatch.persistence.RebalanceRepository;

@Service
public class UnderDeliveryRecoveryJob {
    private static final int BATCH_SIZE = 50;
    private static final System.Logger LOGGER = System.getLogger(
            UnderDeliveryRecoveryJob.class.getName()
    );

    private final RebalanceRepository repository;
    private final RebalanceApplicationService rebalanceService;
    private final Clock clock;

    @Autowired
    public UnderDeliveryRecoveryJob(
            RebalanceRepository repository,
            RebalanceApplicationService rebalanceService
    ) {
        this(repository, rebalanceService, Clock.systemUTC());
    }

    UnderDeliveryRecoveryJob(
            RebalanceRepository repository,
            RebalanceApplicationService rebalanceService,
            Clock clock
    ) {
        this.repository = repository;
        this.rebalanceService = rebalanceService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${voltweave.performance.recovery-poll-delay:1s}")
    public void recover() {
        var now = clock.instant();
        for (var dispatchId : repository.activeDispatchIds(now, BATCH_SIZE)) {
            try {
                rebalanceService.evaluate(dispatchId, now);
            } catch (RuntimeException exception) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Cannot evaluate under-delivery for " + dispatchId, exception);
            }
        }
    }
}
