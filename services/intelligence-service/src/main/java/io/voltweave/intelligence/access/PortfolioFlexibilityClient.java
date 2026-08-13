package io.voltweave.intelligence.access;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PortfolioFlexibilityClient {
    private static final ParameterizedTypeReference<List<PortfolioFlexibilityResource>> RESPONSE =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient restClient;

    public PortfolioFlexibilityClient(RestClient intelligencePortfolioRestClient) {
        this.restClient = intelligencePortfolioRestClient;
    }

    public List<PortfolioFlexibilityResource> resourcesForVpp(UUID vppId) {
        var resources = restClient.get()
                .uri("/internal/v1/vpps/{vppId}/flexibility-resources", vppId)
                .retrieve()
                .body(RESPONSE);
        return resources == null ? List.of() : resources;
    }

    public record PortfolioFlexibilityResource(
            UUID organizationId,
            UUID siteId,
            UUID deviceId,
            String deviceType,
            String status,
            boolean vppOptIn,
            BigDecimal ratedPowerKw,
            BigDecimal capacityKwh,
            BigDecimal maxDischargeKw,
            Integer minimumSocPercent,
            BigDecimal dischargeEfficiency,
            BigDecimal maxChargingKw,
            BigDecimal vehicleBatteryCapacityKwh,
            Integer targetSocPercent,
            BigDecimal chargingEfficiency,
            Instant departureAt
    ) {
    }
}
