package io.voltweave.portfolio.vpp.domain.entities;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.portfolio.vpp.domain.enums.VppMembershipStatus;

public record VppMembership(
        UUID id,
        UUID vppOrganizationId,
        UUID siteOrganizationId,
        UUID vppId,
        UUID siteId,
        VppMembershipStatus status,
        BigDecimal participationWeight,
        Instant joinedAt,
        Instant updatedAt
) {
    public static final BigDecimal DEFAULT_PARTICIPATION_WEIGHT = BigDecimal.ONE;

    public VppMembership {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(vppOrganizationId, "vppOrganizationId is required");
        Objects.requireNonNull(siteOrganizationId, "siteOrganizationId is required");
        Objects.requireNonNull(vppId, "vppId is required");
        Objects.requireNonNull(siteId, "siteId is required");
        Objects.requireNonNull(status, "status is required");
        if (participationWeight == null || participationWeight.signum() <= 0) {
            throw new IllegalArgumentException("participationWeight must be positive");
        }
        Objects.requireNonNull(joinedAt, "joinedAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(joinedAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before joinedAt");
        }
    }

    public static VppMembership active(
            VirtualPowerPlant vpp,
            UUID siteOrganizationId,
            UUID siteId,
            Instant now
    ) {
        return new VppMembership(
                UUID.randomUUID(), vpp.organizationId(), siteOrganizationId,
                vpp.id(), siteId, VppMembershipStatus.ACTIVE,
                DEFAULT_PARTICIPATION_WEIGHT, now, now
        );
    }

    public VppMembership remove(Instant now) {
        if (status != VppMembershipStatus.ACTIVE) {
            throw new IllegalStateException("Only an active membership can be removed");
        }
        return new VppMembership(
                id, vppOrganizationId, siteOrganizationId, vppId, siteId,
                VppMembershipStatus.REMOVED, participationWeight, joinedAt, now
        );
    }

    public VppMembership activate(Instant now) {
        if (status != VppMembershipStatus.REMOVED) {
            throw new IllegalStateException("Only a removed membership can be activated");
        }
        return new VppMembership(
                id, vppOrganizationId, siteOrganizationId, vppId, siteId,
                VppMembershipStatus.ACTIVE, participationWeight, now, now
        );
    }
}
