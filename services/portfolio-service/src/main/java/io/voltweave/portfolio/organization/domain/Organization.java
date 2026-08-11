package io.voltweave.portfolio.organization.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record Organization(
        UUID id,
        OrganizationType type,
        String legalName,
        String displayName,
        String tenantCode,
        OrganizationStatus status,
        String country,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern TENANT_CODE =
            Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$");
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public Organization {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(type, "type is required");
        legalName = requireText(legalName, "legalName", 160);
        displayName = requireText(displayName, "displayName", 120);
        tenantCode = requireText(tenantCode, "tenantCode", 63);
        Objects.requireNonNull(status, "status is required");
        country = requireText(country, "country", 2);
        timezone = requireText(timezone, "timezone", 64);
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        if (!TENANT_CODE.matcher(tenantCode).matches()) {
            throw new IllegalArgumentException("tenantCode must be lowercase kebab-case");
        }
        if (!COUNTRY.matcher(country).matches()) {
            throw new IllegalArgumentException("country must be an uppercase ISO code");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public static Organization active(
            OrganizationType type,
            String legalName,
            String displayName,
            String tenantCode,
            String country,
            String timezone,
            Instant now
    ) {
        return new Organization(
                UUID.randomUUID(), type, legalName, displayName, tenantCode,
                OrganizationStatus.ACTIVE, country, timezone, now, now
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
