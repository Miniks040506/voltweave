package io.voltweave.portfolio.site.application.model;

import io.voltweave.portfolio.site.domain.entities.Site;
import io.voltweave.portfolio.site.domain.entities.SitePreference;

public record SiteProfile(Site site, SitePreference preference) {
}
