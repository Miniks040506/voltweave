package io.voltweave.portfolio.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OrganizationMember(
        UUID id,
        UUID organizationId,
        String subjectId,
        OrganizationRole role,
        MembershipStatus status,
        Instant createdAt
) {
    public OrganizationMember {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");

        if (subjectId == null || subjectId.isBlank()) {
            throw new IllegalArgumentException("subjectId is required");
        }
        subjectId = subjectId.trim();
        if (subjectId.length() > 255) {
            throw new IllegalArgumentException("subjectId exceeds 255 characters");
        }
    }

    public static OrganizationMember active(
            UUID organizationId,
            String subjectId,
            OrganizationRole role,
            Instant now
    ) {
        return new OrganizationMember(
                UUID.randomUUID(), organizationId, subjectId, role,
                MembershipStatus.ACTIVE, now
        );
    }
}
