package io.voltweave.portfolio.access.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.access.application.command.CheckAccessCommand;
import io.voltweave.portfolio.access.application.model.AccessCheckResult;
import io.voltweave.portfolio.access.persistence.AccessCheckRepository;

@Service
public class AccessCheckService {
    private final AccessCheckRepository repository;

    public AccessCheckService(AccessCheckRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AccessCheckResult check(CheckAccessCommand command) {
        return repository.findGrant(
                command.subjectId().trim(), command.resourceType(), command.resourceId()
        ).orElseGet(AccessCheckResult::denied);
    }
}
