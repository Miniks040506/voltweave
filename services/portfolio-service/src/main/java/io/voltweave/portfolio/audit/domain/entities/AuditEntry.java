package io.voltweave.portfolio.audit.domain.entities;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditActorType;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;

public record AuditEntry(
        UUID id,
        UUID organizationId,
        AuditActorType actorType,
        String actorId,
        AuditAction action,
        AuditResourceType resourceType,
        UUID resourceId,
        Instant occurredAt,
        UUID correlationId,
        String ipAddress,
        String userAgent
) {
    public AuditEntry {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(actorType, "actorType is required");
        Objects.requireNonNull(action, "action is required");
        Objects.requireNonNull(resourceType, "resourceType is required");
        Objects.requireNonNull(resourceId, "resourceId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(correlationId, "correlationId is required");
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        actorId = actorId.trim();
    }

    public static AuditEntry userAction(
            UUID organizationId,
            String actorId,
            AuditAction action,
            AuditResourceType resourceType,
            UUID resourceId,
            UUID correlationId,
            Instant now
    ) {
        return new AuditEntry(
                UUID.randomUUID(), organizationId, AuditActorType.USER, actorId,
                action, resourceType, resourceId, now, correlationId, null, null
        );
    }
}
