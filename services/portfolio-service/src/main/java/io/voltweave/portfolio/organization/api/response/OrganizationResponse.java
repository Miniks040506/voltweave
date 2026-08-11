package io.voltweave.portfolio.organization.api.response;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.organization.domain.entities.Organization;
import io.voltweave.portfolio.organization.domain.enums.OrganizationStatus;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;

public record OrganizationResponse(
        UUID id,
        OrganizationType type,
        String legalName,
        String displayName,
        String tenantCode,
        OrganizationStatus status,
        String country,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.id(),
                organization.type(),
                organization.legalName(),
                organization.displayName(),
                organization.tenantCode(),
                organization.status(),
                organization.country(),
                organization.timezone(),
                organization.createdAt(),
                organization.updatedAt()
        );
    }
}
