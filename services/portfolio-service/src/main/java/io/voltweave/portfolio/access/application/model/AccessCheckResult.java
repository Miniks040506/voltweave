package io.voltweave.portfolio.access.application.model;

import java.util.UUID;

import io.voltweave.portfolio.organization.domain.enums.OrganizationRole;

public record AccessCheckResult(
        boolean allowed,
        UUID organizationId,
        OrganizationRole organizationRole
) {
    public static AccessCheckResult denied() {
        return new AccessCheckResult(false, null, null);
    }
}
