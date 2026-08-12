package io.voltweave.portfolio.site.application.command;

public record UpdateSitePreferenceCommand(
        boolean vppOptIn,
        int minimumBatteryReservePercent
) {
}
