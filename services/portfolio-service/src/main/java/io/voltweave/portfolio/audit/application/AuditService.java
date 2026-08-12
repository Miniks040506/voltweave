package io.voltweave.portfolio.audit.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.audit.domain.entities.AuditEntry;
import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;
import io.voltweave.portfolio.audit.persistence.AuditEntryRepository;

@Service
public class AuditService {
    private final AuditEntryRepository repository;

    public AuditService(AuditEntryRepository repository) {
        this.repository = repository;
    }

    public void recordUserAction(
            UUID organizationId,
            String actorSubjectId,
            AuditAction action,
            AuditResourceType resourceType,
            UUID resourceId
    ) {
        repository.insert(AuditEntry.userAction(
                organizationId, actorSubjectId, action, resourceType, resourceId,
                correlationId(), Instant.now()
        ));
    }

    @Transactional(readOnly = true)
    public List<AuditEntry> findForSubject(
            UUID organizationId,
            String subjectId,
            int limit
    ) {
        return repository.findForSubject(organizationId, subjectId, limit);
    }

    private static UUID correlationId() {
        String value = MDC.get("correlationId");
        if (value != null) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // CorrelationIdFilter supplies valid UUIDs; non-HTTP callers receive a new one.
            }
        }
        return UUID.randomUUID();
    }
}
