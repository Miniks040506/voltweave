package io.voltweave.portfolio.vpp.application.exception;

import java.util.UUID;

public class VppNotFoundException extends RuntimeException {
    public VppNotFoundException(UUID vppId) {
        super("VPP not found: " + vppId);
    }
}
