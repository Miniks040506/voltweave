package io.voltweave.portfolio.organization.application;

import io.voltweave.portfolio.organization.domain.enums.OrganizationType;

public record CreateOrganizationCommand(
        OrganizationType type,
        String legalName,
        String displayName,
        String tenantCode,
        String country,
        String timezone
) {
}
