package io.voltweave.portfolio.site.api.response;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.site.application.SiteProfile;
import io.voltweave.portfolio.site.domain.enums.SiteStatus;

public record SiteResponse(
        UUID id,
        UUID organizationId,
        String name,
        String timezone,
        String region,
        String country,
        SiteStatus status,
        boolean vppOptIn,
        int minimumBatteryReservePercent,
        Instant createdAt,
        Instant updatedAt,
        Instant preferenceUpdatedAt
) {
    public static SiteResponse from(SiteProfile profile) {
        var site = profile.site();
        var preference = profile.preference();
        return new SiteResponse(
                site.id(),
                site.organizationId(),
                site.name(),
                site.timezone(),
                site.region(),
                site.country(),
                site.status(),
                preference.vppOptIn(),
                preference.minimumBatteryReservePercent(),
                site.createdAt(),
                site.updatedAt(),
                preference.updatedAt()
        );
    }
}
