package io.voltweave.portfolio.organization.api.request;

import io.voltweave.portfolio.organization.domain.enums.OrganizationRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AddOrganizationMemberRequest(
        @NotBlank @Size(max = 255) String subjectId,
        @NotNull OrganizationRole role
) {
}
