package io.voltweave.portfolio.site.api.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateSitePreferenceRequest(
        @NotNull Boolean vppOptIn,
        @NotNull @Min(0) @Max(100) Integer minimumBatteryReservePercent
) {
}
