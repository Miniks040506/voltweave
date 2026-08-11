package io.voltweave.portfolio.site.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSiteRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 64) String timezone,
        @NotBlank @Size(max = 120) String region,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String country
) {
}
