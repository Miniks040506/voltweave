package io.voltweave.portfolio.vpp.domain.entities;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import io.voltweave.portfolio.vpp.domain.enums.VppStatus;

public record VirtualPowerPlant(
        UUID id,
        UUID organizationId,
        String name,
        String region,
        VppStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public VirtualPowerPlant {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        name = requireText(name, "name", 120);
        region = requireText(region, "region", 120);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public static VirtualPowerPlant active(
            UUID organizationId,
            String name,
            String region,
            Instant now
    ) {
        return new VirtualPowerPlant(
                UUID.randomUUID(), organizationId, name, region, VppStatus.ACTIVE, now, now
        );
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        var trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        }
        return trimmed;
    }
}
