package io.voltweave.portfolio.site.application.exception;

import java.util.UUID;

public class SiteNotFoundException extends RuntimeException {
    public SiteNotFoundException(UUID siteId) {
        super("Site not found: " + siteId);
    }
}
