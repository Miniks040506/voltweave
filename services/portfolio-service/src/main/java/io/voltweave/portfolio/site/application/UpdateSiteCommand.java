package io.voltweave.portfolio.site.application;

public record UpdateSiteCommand(
        String name,
        String timezone,
        String region,
        String country
) {
}
