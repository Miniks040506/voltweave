package io.voltweave.portfolio.site.application;

import java.util.UUID;

public record CreateSiteCommand(
        UUID organizationId,
        String name,
        String timezone,
        String region,
        String country
) {
}
