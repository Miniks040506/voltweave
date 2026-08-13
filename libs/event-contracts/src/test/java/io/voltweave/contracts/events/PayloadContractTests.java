package io.voltweave.contracts.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.voltweave.contracts.events.audit.v1.AuditRecordedPayloadV1;
import io.voltweave.contracts.events.dispatch.v1.DispatchCompletedPayloadV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioChangeTypeV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioLifecyclePayloadV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioResourceTypeV1;

class PayloadContractTests {
    @Test
    void buildsPortfolioLifecyclePayload() {
        UUID siteId = UUID.randomUUID();
        UUID vppId = UUID.randomUUID();

        var payload = new PortfolioLifecyclePayloadV1(
                siteId, PortfolioResourceTypeV1.VPP_MEMBERSHIP,
                PortfolioChangeTypeV1.ADDED, vppId
        );

        assertThat(payload.resourceId()).isEqualTo(siteId);
        assertThat(payload.relatedResourceId()).isEqualTo(vppId);
    }

    @Test
    void rejectsIncompletePayloads() {
        assertThatThrownBy(() -> new PortfolioLifecyclePayloadV1(
                null, PortfolioResourceTypeV1.DEVICE,
                PortfolioChangeTypeV1.PROVISION_REQUESTED, null
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resourceId");

        assertThatThrownBy(() -> new AuditRecordedPayloadV1(
                UUID.randomUUID(), "USER", " ", "ACTION", "DEVICE", UUID.randomUUID()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorId");
    }

    @Test
    void acceptsOnlyFinalDispatchStatuses() {
        Instant now = Instant.parse("2026-08-13T02:00:00Z");

        assertThatThrownBy(() -> new DispatchCompletedPayloadV1(
                UUID.randomUUID(), UUID.randomUUID(), "ACTIVE",
                BigDecimal.TEN, BigDecimal.ONE, UUID.randomUUID(), 1,
                now.minusSeconds(300), now, now
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completionStatus");
    }
}
