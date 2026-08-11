package io.voltweave.portfolio.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrganizationTests {
    private static final Instant NOW = Instant.parse("2026-08-11T12:00:00Z");

    @Test
    void normalizesHumanReadableNames() {
        var organization = Organization.active(
                OrganizationType.COMMERCIAL_CUSTOMER,
                "  Example Legal Name  ",
                "  Example Display Name  ",
                "example-tenant",
                "VN",
                "Asia/Ho_Chi_Minh",
                NOW
        );

        assertThat(organization.legalName()).isEqualTo("Example Legal Name");
        assertThat(organization.displayName()).isEqualTo("Example Display Name");
        assertThat(organization.createdAt()).isEqualTo(NOW);
        assertThat(organization.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsInvalidTenantCode() {
        assertThatIllegalArgumentException().isThrownBy(() -> Organization.active(
                OrganizationType.COMMERCIAL_CUSTOMER,
                "Example Legal Name",
                "Example",
                "Upper_Case",
                "VN",
                "Asia/Ho_Chi_Minh",
                NOW
        ));
    }
}
