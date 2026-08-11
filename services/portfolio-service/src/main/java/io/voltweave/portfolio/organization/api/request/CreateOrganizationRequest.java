package io.voltweave.portfolio.organization.api.request;

import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotNull OrganizationType type,
        @NotBlank @Size(max = 160) String legalName,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$") String tenantCode,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country,
        @NotBlank @Size(max = 64) String timezone
) {
}
