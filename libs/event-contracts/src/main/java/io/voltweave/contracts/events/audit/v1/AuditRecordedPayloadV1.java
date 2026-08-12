package io.voltweave.contracts.events.audit.v1;

import java.util.Objects;
import java.util.UUID;

public record AuditRecordedPayloadV1(
        UUID auditEntryId,
        String actorType,
        String actorId,
        String action,
        String resourceType,
        UUID resourceId
) {
    public AuditRecordedPayloadV1 {
        Objects.requireNonNull(auditEntryId, "auditEntryId is required");
        Objects.requireNonNull(resourceId, "resourceId is required");
        actorType = requireText(actorType, "actorType");
        actorId = requireText(actorId, "actorId");
        action = requireText(action, "action");
        resourceType = requireText(resourceType, "resourceType");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
