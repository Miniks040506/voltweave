package io.voltweave.portfolio.organization.service;

import io.voltweave.portfolio.organization.domain.OrganizationType;

public record CreateOrganizationCommand(
        OrganizationType type,
        String legalName,
        String displayName,
        String tenantCode,
        String country,
        String timezone
) {
}
