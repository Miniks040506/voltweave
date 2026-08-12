package io.voltweave.portfolio.access.api.response;

import java.util.UUID;

import io.voltweave.portfolio.access.application.model.AccessCheckResult;
import io.voltweave.portfolio.organization.domain.enums.OrganizationRole;

public record AccessCheckResponse(
        boolean allowed,
        UUID organizationId,
        OrganizationRole organizationRole
) {
    public static AccessCheckResponse from(AccessCheckResult result) {
        return new AccessCheckResponse(
                result.allowed(), result.organizationId(), result.organizationRole()
        );
    }
}
