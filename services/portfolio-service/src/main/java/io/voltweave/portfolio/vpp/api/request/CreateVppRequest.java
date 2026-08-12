package io.voltweave.portfolio.vpp.api.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateVppRequest(
        @NotNull UUID organizationId,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String region
) {
}
