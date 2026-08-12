package io.voltweave.portfolio.site.application.command;

public record UpdateSiteCommand(
        String name,
        String timezone,
        String region,
        String country
) {
}
