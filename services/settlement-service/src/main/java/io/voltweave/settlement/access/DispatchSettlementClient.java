package io.voltweave.settlement.access;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class DispatchSettlementClient {
    private final RestClient restClient;

    public DispatchSettlementClient(RestClient settlementDispatchRestClient) {
        this.restClient = settlementDispatchRestClient;
    }

    public SettlementInput input(UUID dispatchId) {
        SettlementInput input;
        try {
            input = restClient.get()
                    .uri("/internal/v1/dispatches/{dispatchId}/settlement-input", dispatchId)
                    .retrieve().body(SettlementInput.class);
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException("Dispatch settlement input was rejected", exception);
        }
        if (input == null) {
            throw new IllegalStateException("Dispatch settlement input is unavailable");
        }
        return input;
    }

    public record SettlementInput(
            UUID organizationId,
            UUID dispatchId,
            UUID vppId,
            String completionStatus,
            BigDecimal targetPowerKw,
            Instant scheduledStartAt,
            Instant scheduledEndAt,
            Instant frozenAt,
            UUID baselineId,
            long baselineVersion,
            String baselineModelName,
            String baselineModelVersion,
            List<BaselinePoint> baselinePoints,
            List<Participant> participants
    ) {
    }

    public record BaselinePoint(Instant forecastAt, BigDecimal baselineGridImportKw) {
    }

    public record Participant(
            UUID siteId,
            UUID deviceId,
            String deviceType,
            BigDecimal requestedPowerKw,
            BigDecimal expectedEnergyKwh,
            BigDecimal deliveredEnergyKwh
    ) {
    }
}
