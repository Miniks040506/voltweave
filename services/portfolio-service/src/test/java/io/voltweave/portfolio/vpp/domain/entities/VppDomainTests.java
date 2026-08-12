package io.voltweave.portfolio.vpp.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.portfolio.vpp.domain.enums.VppMembershipStatus;

class VppDomainTests {
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void createsAnActiveMembershipWithSafeWeight() {
        var vpp = VirtualPowerPlant.active(UUID.randomUUID(), "HCM Fleet", "VN-HCM", NOW);
        var membership = VppMembership.active(
                vpp, UUID.randomUUID(), UUID.randomUUID(), NOW
        );

        assertThat(membership.status()).isEqualTo(VppMembershipStatus.ACTIVE);
        assertThat(membership.participationWeight())
                .isEqualByComparingTo(VppMembership.DEFAULT_PARTICIPATION_WEIGHT);
    }

    @Test
    void removesAnActiveMembershipOnlyOnce() {
        var vpp = VirtualPowerPlant.active(UUID.randomUUID(), "HCM Fleet", "VN-HCM", NOW);
        var removed = VppMembership.active(
                vpp, UUID.randomUUID(), UUID.randomUUID(), NOW
        ).remove(NOW.plusSeconds(1));

        assertThat(removed.status()).isEqualTo(VppMembershipStatus.REMOVED);
        assertThatThrownBy(() -> removed.remove(NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }
}
