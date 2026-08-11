package io.voltweave.portfolio.site.application;

import io.voltweave.portfolio.site.domain.entity.Site;
import io.voltweave.portfolio.site.domain.entity.SitePreference;

public record SiteProfile(Site site, SitePreference preference) {
}
