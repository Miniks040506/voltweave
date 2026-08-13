package io.voltweave.intelligence.flexibility.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record GenerateFlexibilitySnapshotRequest(
        @NotNull @Min(1) @Max(1_440) Integer dispatchDurationMinutes
) {
}
