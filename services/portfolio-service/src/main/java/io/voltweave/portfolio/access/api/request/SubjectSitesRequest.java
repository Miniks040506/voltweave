package io.voltweave.portfolio.access.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectSitesRequest(
        @NotBlank @Size(max = 255) String subjectId
) {
}
