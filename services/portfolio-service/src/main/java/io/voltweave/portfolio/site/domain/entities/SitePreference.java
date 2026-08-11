package io.voltweave.portfolio.site.domain.entities;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SitePreference(
        UUID organizationId,
        UUID siteId,
        boolean vppOptIn,
        int minimumBatteryReservePercent,
        Instant updatedAt
) {
    public static final int DEFAULT_RESERVE_PERCENT = 20;

    public SitePreference {
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(siteId, "siteId is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (minimumBatteryReservePercent < 0 || minimumBatteryReservePercent > 100) {
            throw new IllegalArgumentException(
                    "minimumBatteryReservePercent must be between 0 and 100"
            );
        }
    }

    public static SitePreference defaults(UUID organizationId, UUID siteId, Instant now) {
        return new SitePreference(
                organizationId, siteId, false, DEFAULT_RESERVE_PERCENT, now
        );
    }

    public SitePreference update(boolean optIn, int reservePercent, Instant now) {
        return new SitePreference(organizationId, siteId, optIn, reservePercent, now);
    }
}
