package io.voltweave.portfolio.audit.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.audit.v1.AuditRecordedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.portfolio.audit.domain.entities.AuditEntry;
import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;
import io.voltweave.portfolio.audit.persistence.AuditEntryRepository;
import io.voltweave.portfolio.messaging.application.OutboxService;

@Service
public class AuditService {
    private final AuditEntryRepository repository;
    private final OutboxService outboxService;

    public AuditService(AuditEntryRepository repository, OutboxService outboxService) {
        this.repository = repository;
        this.outboxService = outboxService;
    }

    public AuditEntry recordUserAction(
            UUID organizationId,
            String actorSubjectId,
            AuditAction action,
            AuditResourceType resourceType,
            UUID resourceId
    ) {
        var entry = AuditEntry.userAction(
                organizationId, actorSubjectId, action, resourceType, resourceId,
                correlationId(), Instant.now()
        );
        repository.insert(entry);
        outboxService.enqueue(EventTopics.AUDIT_V1, EventEnvelopeV1.create(
                EventTypes.AUDIT_RECORDED, "portfolio-service", organizationId,
                entry.correlationId(), null, organizationId.toString(),
                new AuditRecordedPayloadV1(
                        entry.id(), entry.actorType().name(), entry.actorId(),
                        entry.action().name(), entry.resourceType().name(), entry.resourceId()
                ), entry.occurredAt()
        ));
        return entry;
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
