package io.voltweave.portfolio.vpp.application.exception;

import java.util.UUID;

public class VppMembershipNotFoundException extends RuntimeException {
    public VppMembershipNotFoundException(UUID siteId) {
        super("Active VPP membership not found for site: " + siteId);
    }
}
