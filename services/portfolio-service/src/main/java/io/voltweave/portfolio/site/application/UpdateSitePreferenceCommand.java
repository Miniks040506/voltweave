package io.voltweave.portfolio.site.application;

public record UpdateSitePreferenceCommand(
        boolean vppOptIn,
        int minimumBatteryReservePercent
) {
}
