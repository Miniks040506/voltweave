package io.voltweave.portfolio.site.domain.entity;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import io.voltweave.portfolio.site.domain.enums.SiteStatus;

public record Site(
        UUID id,
        UUID organizationId,
        String name,
        String timezone,
        String region,
        String country,
        SiteStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern COUNTRY = Pattern.compile("^[A-Z]{2}$");

    public Site {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        name = requireText(name, "name", 120);
        timezone = requireText(timezone, "timezone", 64);
        region = requireText(region, "region", 120);
        country = requireText(country, "country", 2);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        try {
            ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("timezone must be a valid IANA zone", exception);
        }
        if (!COUNTRY.matcher(country).matches()) {
            throw new IllegalArgumentException("country must be an uppercase ISO code");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt cannot be before createdAt");
        }
    }

    public static Site active(
            UUID organizationId,
            String name,
            String timezone,
            String region,
            String country,
            Instant now
    ) {
        return new Site(
                UUID.randomUUID(), organizationId, name, timezone, region, country,
                SiteStatus.ACTIVE, now, now
        );
    }

    public Site updateDetails(
            String name,
            String timezone,
            String region,
            String country,
            Instant now
    ) {
        return new Site(
                id, organizationId, name, timezone, region, country,
                status, createdAt, now
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
