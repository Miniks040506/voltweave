package io.voltweave.portfolio.vpp.api.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.portfolio.vpp.application.VppProfile;
import io.voltweave.portfolio.vpp.domain.enums.VppStatus;

public record VppResponse(
        UUID id,
        UUID organizationId,
        String name,
        String region,
        VppStatus status,
        AutomationPolicyResponse automationPolicy,
        List<VppMembershipResponse> memberships,
        Instant createdAt,
        Instant updatedAt
) {
    public static VppResponse from(VppProfile profile) {
        var vpp = profile.vpp();
        return new VppResponse(
                vpp.id(), vpp.organizationId(), vpp.name(), vpp.region(), vpp.status(),
                AutomationPolicyResponse.from(profile.automationPolicy()),
                profile.memberships().stream().map(VppMembershipResponse::from).toList(),
                vpp.createdAt(), vpp.updatedAt()
        );
    }
}
