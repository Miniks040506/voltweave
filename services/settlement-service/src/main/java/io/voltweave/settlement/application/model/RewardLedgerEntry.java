package io.voltweave.settlement.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RewardLedgerEntry(
        UUID id,
        UUID organizationId,
        UUID settlementId,
        UUID participantId,
        String entryType,
        BigDecimal energyKwh,
        BigDecimal ratePerKwh,
        BigDecimal amount,
        String currency,
        UUID sourceEventId,
        String idempotencyKey,
        String reason,
        String createdBy,
        Instant createdAt
) {
}
