package io.voltweave.dispatch.api.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDispatchRequest(
        @NotNull UUID vppId,
        @NotNull UUID optimizationPreviewId,
        @NotBlank String type,
        @NotNull Instant scheduledStartAt,
        @Min(15) @Max(1440) int durationMinutes
) {
}
