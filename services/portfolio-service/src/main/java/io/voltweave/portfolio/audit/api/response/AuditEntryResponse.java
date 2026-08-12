package io.voltweave.portfolio.audit.api.response;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.audit.domain.entities.AuditEntry;
import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditActorType;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;

public record AuditEntryResponse(
        UUID id,
        UUID organizationId,
        AuditActorType actorType,
        String actorId,
        AuditAction action,
        AuditResourceType resourceType,
        UUID resourceId,
        Instant occurredAt,
        UUID correlationId
) {
    public static AuditEntryResponse from(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.id(), entry.organizationId(), entry.actorType(), entry.actorId(),
                entry.action(), entry.resourceType(), entry.resourceId(),
                entry.occurredAt(), entry.correlationId()
        );
    }
}
