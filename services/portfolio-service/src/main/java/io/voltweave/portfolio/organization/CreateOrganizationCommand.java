package io.voltweave.portfolio.organization;

public record CreateOrganizationCommand(
        OrganizationType type,
        String legalName,
        String displayName,
        String tenantCode,
        String country,
        String timezone
) {
}
