package io.voltweave.settlement.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import io.voltweave.settlement.application.model.RewardLedgerEntry;

public record RewardAdjustmentResponse(
        UUID id,
        UUID settlementId,
        UUID siteId,
        BigDecimal amount,
        String currency,
        String reason,
        Instant createdAt
) {
    public static RewardAdjustmentResponse from(RewardLedgerEntry entry) {
        return new RewardAdjustmentResponse(
                entry.id(), entry.settlementId(), entry.participantId(), entry.amount(),
                entry.currency(), entry.reason(), entry.createdAt()
        );
    }
}
