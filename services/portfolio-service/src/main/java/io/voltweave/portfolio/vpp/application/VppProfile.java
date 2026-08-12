package io.voltweave.portfolio.vpp.application;

import java.util.List;

import io.voltweave.portfolio.vpp.domain.entities.AutomationPolicy;
import io.voltweave.portfolio.vpp.domain.entities.VirtualPowerPlant;
import io.voltweave.portfolio.vpp.domain.entities.VppMembership;

public record VppProfile(
        VirtualPowerPlant vpp,
        AutomationPolicy automationPolicy,
        List<VppMembership> memberships
) {
    public VppProfile {
        memberships = List.copyOf(memberships);
    }
}
