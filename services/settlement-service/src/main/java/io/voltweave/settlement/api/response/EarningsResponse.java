package io.voltweave.settlement.api.response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.settlement.application.model.RewardLedgerEntry;

public record EarningsResponse(
        BigDecimal totalAmount,
        String currency,
        List<Entry> entries
) {
    public static EarningsResponse from(List<RewardLedgerEntry> entries) {
        BigDecimal total = entries.stream().map(RewardLedgerEntry::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(4, RoundingMode.HALF_UP);
        return new EarningsResponse(
                total, "VWC", entries.stream().map(Entry::from).toList()
        );
    }

    public record Entry(
            UUID id,
            UUID settlementId,
            UUID siteId,
            String entryType,
            BigDecimal energyKwh,
            BigDecimal ratePerKwh,
            BigDecimal amount,
            String reason,
            Instant createdAt
    ) {
        private static Entry from(RewardLedgerEntry entry) {
            return new Entry(
                    entry.id(), entry.settlementId(), entry.participantId(),
                    entry.entryType(), entry.energyKwh(), entry.ratePerKwh(),
                    entry.amount(), entry.reason(), entry.createdAt()
            );
        }
    }
}
