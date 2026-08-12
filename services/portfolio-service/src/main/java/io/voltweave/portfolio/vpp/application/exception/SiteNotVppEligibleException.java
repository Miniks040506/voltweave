package io.voltweave.portfolio.vpp.application.exception;

import java.util.UUID;

public class SiteNotVppEligibleException extends RuntimeException {
    public SiteNotVppEligibleException(UUID siteId) {
        super("Site is not eligible for VPP membership: " + siteId);
    }
}
