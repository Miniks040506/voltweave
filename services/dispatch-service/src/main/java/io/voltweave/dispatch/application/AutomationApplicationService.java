package io.voltweave.dispatch.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.dispatch.application.model.AutomationPlan;
import io.voltweave.dispatch.application.model.AutomationPolicy;
import io.voltweave.dispatch.application.model.CreateDispatchCommand;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.persistence.AutomationRepository;

@Service
public class AutomationApplicationService {
    private final AutomationRepository repository;
    private final DispatchApplicationService dispatchService;
    private final CommandApplicationService commandService;
    private final Clock clock;

    @Autowired
    public AutomationApplicationService(
            AutomationRepository repository,
            DispatchApplicationService dispatchService,
            CommandApplicationService commandService
    ) {
        this(repository, dispatchService, commandService, Clock.systemUTC());
    }

    AutomationApplicationService(
            AutomationRepository repository,
            DispatchApplicationService dispatchService,
            CommandApplicationService commandService,
            Clock clock
    ) {
        this.repository = repository;
        this.dispatchService = dispatchService;
        this.commandService = commandService;
        this.clock = clock;
    }

    @Transactional
    public Optional<Dispatch> createCandidate(AutomationPolicy policy, AutomationPlan plan) {
        if (!plan.feasible()) {
            return Optional.empty();
        }
        repository.lock(policy, plan.scheduledStartAt());
        var existing = repository.findDispatch(
                policy.id(), policy.version(), plan.scheduledStartAt()
        );
        if (existing.isPresent()) {
            return dispatchService.find(existing.orElseThrow());
        }

        String idempotencyKey = "automation:" + policy.id() + ":"
                + policy.version() + ":" + plan.scheduledStartAt().getEpochSecond();
        Dispatch dispatch = dispatchService.create(new CreateDispatchCommand(
                policy.organizationId(), policy.vppId(), plan.optimizationPreviewId(),
                "REDUCE_DEMAND", plan.scheduledStartAt(), plan.duration(),
                "automation:" + policy.id(), idempotencyKey
        ));
        repository.insert(policy, plan, dispatch.id(), clock.instant());
        if ("AUTO_DISPATCH".equals(policy.approvalMode())) {
            commandService.prepare(dispatch.id(), correlationId(policy, plan));
        }
        return Optional.of(dispatch);
    }

    private static UUID correlationId(AutomationPolicy policy, AutomationPlan plan) {
        return UUID.nameUUIDFromBytes((policy.id() + ":" + policy.version() + ":"
                + plan.scheduledStartAt()).getBytes(StandardCharsets.UTF_8));
    }
}
