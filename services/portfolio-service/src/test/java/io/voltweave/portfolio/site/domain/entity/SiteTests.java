package io.voltweave.portfolio.site.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class SiteTests {
    @Test
    void normalizesSiteDetails() {
        var site = Site.active(
                UUID.randomUUID(), "  Home  ", "Asia/Ho_Chi_Minh",
                "  Ho Chi Minh City  ", "VN", Instant.parse("2026-08-12T00:00:00Z")
        );

        assertThat(site.name()).isEqualTo("Home");
        assertThat(site.region()).isEqualTo("Ho Chi Minh City");
    }

    @Test
    void rejectsUnknownTimezone() {
        assertThatThrownBy(() -> Site.active(
                UUID.randomUUID(), "Home", "Mars/Olympus", "HCMC", "VN", Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timezone must be a valid IANA zone");
    }

    @Test
    void protectsBatteryReserveRange() {
        assertThatThrownBy(() -> new SitePreference(
                UUID.randomUUID(), UUID.randomUUID(), true, 101, Instant.now()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 100");
    }
}
