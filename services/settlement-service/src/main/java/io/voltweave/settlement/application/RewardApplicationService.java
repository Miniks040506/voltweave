package io.voltweave.settlement.application;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.settlement.application.model.RewardLedgerEntry;
import io.voltweave.settlement.application.model.Settlement;
import io.voltweave.settlement.exception.IdempotencyConflictException;
import io.voltweave.settlement.persistence.RewardLedgerRepository;

@Service
public class RewardApplicationService {
    private final RewardLedgerRepository repository;

    public RewardApplicationService(RewardLedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RewardLedgerEntry> earningsForSites(List<UUID> siteIds) {
        return repository.findByParticipantIds(siteIds);
    }

    @Transactional
    public RewardLedgerEntry adjust(
            Settlement settlement,
            UUID siteId,
            BigDecimal amount,
            String reason,
            String createdBy,
            String idempotencyKey
    ) {
        String key = validate(settlement, siteId, amount, reason, createdBy, idempotencyKey);
        BigDecimal normalizedAmount = amount.setScale(4, RoundingMode.HALF_UP);
        String normalizedReason = reason.trim();
        String requestHash = sha256(String.join("|",
                settlement.id().toString(), siteId.toString(),
                normalizedAmount.toPlainString(), normalizedReason
        ));
        repository.lockAdjustmentIdempotency(settlement.organizationId(), key);
        var existing = repository.findAdjustmentIdempotency(settlement.organizationId(), key);
        if (existing.isPresent()) {
            if (!existing.orElseThrow().requestHash().equals(requestHash)) {
                throw new IdempotencyConflictException();
            }
            return repository.findById(existing.orElseThrow().resourceId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record references a missing reward entry"
                    ));
        }
        var entry = new RewardLedgerEntry(
                UUID.randomUUID(), settlement.organizationId(), settlement.id(), siteId,
                "ADJUSTMENT", null, null, normalizedAmount, "VWC", null, key,
                normalizedReason, createdBy.trim(), Instant.now()
        );
        repository.insertAdjustment(entry, requestHash);
        return entry;
    }

    private static String validate(
            Settlement settlement,
            UUID siteId,
            BigDecimal amount,
            String reason,
            String createdBy,
            String idempotencyKey
    ) {
        if (settlement == null || siteId == null || amount == null
                || reason == null || reason.isBlank() || reason.length() > 500
                || createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("Adjustment fields are invalid");
        }
        if (amount.signum() == 0 || amount.stripTrailingZeros().scale() > 4) {
            throw new IllegalArgumentException("Adjustment amount must be non-zero with 4 decimals");
        }
        if (settlement.lines().stream().noneMatch(line -> line.siteId().equals(siteId))) {
            throw new IllegalArgumentException("Site is not part of the settlement");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || idempotencyKey.trim().length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1..100 characters");
        }
        return idempotencyKey.trim();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
