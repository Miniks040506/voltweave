package io.voltweave.portfolio.vpp.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.portfolio.vpp.domain.enums.ApprovalMode;
import io.voltweave.portfolio.vpp.domain.enums.AutomationTriggerType;

class AutomationPolicyTests {
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void startsDisabledAndIncrementsVersionOnUpdate() {
        var vpp = VirtualPowerPlant.active(UUID.randomUUID(), "HCM Fleet", "VN-HCM", NOW);
        var defaults = AutomationPolicy.disabledDefaults(vpp, NOW);
        var updated = defaults.update(
                true, AutomationTriggerType.PEAK_LIMIT, ApprovalMode.REQUIRE_OPERATOR,
                new BigDecimal("500"), null, 15, new BigDecimal("250"),
                30, 10, 30, 60, NOW.plusSeconds(60), NOW.plusSeconds(1)
        );

        assertThat(defaults.enabled()).isFalse();
        assertThat(updated.version()).isEqualTo(2);
        assertThat(updated.peakImportLimitKw()).isEqualByComparingTo("500");
    }

    @Test
    void rejectsThresholdThatDoesNotMatchTrigger() {
        var vpp = VirtualPowerPlant.active(UUID.randomUUID(), "HCM Fleet", "VN-HCM", NOW);

        assertThatThrownBy(() -> AutomationPolicy.disabledDefaults(vpp, NOW).update(
                true, AutomationTriggerType.PRICE_THRESHOLD, ApprovalMode.AUTO_DISPATCH,
                new BigDecimal("500"), null, 10, BigDecimal.ONE,
                15, 10, 30, 60, NOW, NOW
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Threshold");
    }
}
