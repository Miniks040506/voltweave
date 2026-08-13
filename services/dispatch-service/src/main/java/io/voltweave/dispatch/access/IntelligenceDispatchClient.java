package io.voltweave.dispatch.access;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class IntelligenceDispatchClient {
    private final RestClient restClient;

    public IntelligenceDispatchClient(RestClient dispatchIntelligenceRestClient) {
        this.restClient = dispatchIntelligenceRestClient;
    }

    public DispatchInput input(
            UUID organizationId,
            UUID vppId,
            UUID previewId,
            Instant startAt,
            Instant endAt
    ) {
        DispatchInput input;
        try {
            input = restClient.get()
                    .uri(builder -> builder
                            .path("/internal/v1/vpps/{vppId}/dispatch-inputs/{previewId}")
                            .queryParam("organizationId", organizationId)
                            .queryParam("startAt", startAt)
                            .queryParam("endAt", endAt)
                            .build(vppId, previewId))
                    .retrieve().body(DispatchInput.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Intelligence dispatch input was rejected", exception);
        }
        if (input == null) {
            throw new IllegalStateException("Intelligence dispatch input is unavailable");
        }
        return input;
    }

    public ReplacementPlan replacementPlan(
            UUID organizationId,
            UUID vppId,
            BigDecimal missingPowerKw,
            int reserveMarginPercent,
            Duration remainingDuration,
            Set<UUID> excludedDeviceIds
    ) {
        ReplacementPlan plan;
        try {
            plan = restClient.post().uri(builder -> builder
                            .path("/internal/v1/vpps/{vppId}/replacement-plans")
                            .queryParam("organizationId", organizationId)
                            .build(vppId))
                    .body(new ReplacementRequest(
                            missingPowerKw, BigDecimal.valueOf(reserveMarginPercent),
                            remainingDuration.toSeconds(), excludedDeviceIds
                    ))
                    .retrieve().body(ReplacementPlan.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Intelligence replacement plan was rejected", exception);
        }
        if (plan == null) {
            throw new IllegalStateException("Intelligence replacement plan is unavailable");
        }
        return plan;
    }

    public record DispatchInput(
            UUID optimizationPreviewId, long optimizationPreviewVersion,
            UUID organizationId, UUID vppId,
            BigDecimal targetPowerKw, BigDecimal requiredPowerKw, BigDecimal plannedPowerKw,
            boolean feasible, UUID forecastId, long forecastVersion,
            String forecastModelName, String forecastModelVersion, Instant forecastValidUntil,
            List<Allocation> allocations, List<BaselinePoint> baselinePoints
    ) {
    }

    public record Allocation(
            UUID siteId, UUID deviceId, String deviceType,
            BigDecimal availablePowerKw, BigDecimal availableEnergyKwh,
            BigDecimal reliability, BigDecimal availableSoc, BigDecimal responseSpeed,
            BigDecimal lowDegradationCost, BigDecimal customerPreference,
            BigDecimal score, BigDecimal allocatedPowerKw, boolean eligible
    ) {
    }

    public record BaselinePoint(Instant forecastAt, BigDecimal baselineGridImportKw) {
    }

    private record ReplacementRequest(
            BigDecimal missingPowerKw,
            BigDecimal reserveMarginPercent,
            long remainingSeconds,
            Set<UUID> excludedDeviceIds
    ) {
    }

    public record ReplacementPlan(
            UUID id,
            BigDecimal plannedPowerKw,
            boolean feasible,
            List<Allocation> candidates
    ) {
    }
}
