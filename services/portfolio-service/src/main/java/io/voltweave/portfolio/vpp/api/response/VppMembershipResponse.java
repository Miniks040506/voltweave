package io.voltweave.portfolio.vpp.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.portfolio.vpp.domain.entities.VppMembership;
import io.voltweave.portfolio.vpp.domain.enums.VppMembershipStatus;

public record VppMembershipResponse(
        UUID id,
        UUID siteOrganizationId,
        UUID siteId,
        VppMembershipStatus status,
        BigDecimal participationWeight,
        Instant joinedAt,
        Instant updatedAt
) {
    public static VppMembershipResponse from(VppMembership membership) {
        return new VppMembershipResponse(
                membership.id(), membership.siteOrganizationId(), membership.siteId(),
                membership.status(), membership.participationWeight(),
                membership.joinedAt(), membership.updatedAt()
        );
    }
}
