package io.voltweave.dispatch.access;

import java.util.List;
import java.util.UUID;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.voltweave.dispatch.application.model.AutomationPolicy;

@Component
public class PortfolioAccessClient {
    private final RestClient restClient;

    public PortfolioAccessClient(RestClient dispatchPortfolioRestClient) {
        this.restClient = dispatchPortfolioRestClient;
    }

    public UUID requireVppAccess(String subjectId, UUID vppId) {
        var response = restClient.post().uri("/internal/v1/access-checks")
                .body(new AccessCheckRequest(subjectId, "VPP", vppId))
                .retrieve().body(AccessCheckResponse.class);
        if (response == null || !response.allowed() || response.organizationId() == null) {
            throw new AccessDeniedException("VPP access denied");
        }
        return response.organizationId();
    }

    public RecoveryPolicy recoveryPolicy(UUID organizationId, UUID vppId) {
        var response = restClient.get().uri(builder -> builder
                        .path("/internal/v1/vpps/{vppId}/automation-policy")
                        .queryParam("organizationId", organizationId)
                        .build(vppId))
                .retrieve().body(RecoveryPolicy.class);
        if (response == null) {
            throw new IllegalStateException("VPP recovery policy is unavailable");
        }
        return response;
    }

    public List<AutomationPolicy> activeAutomationPolicies() {
        var response = restClient.get().uri("/internal/v1/automation-policies")
                .retrieve().body(new ParameterizedTypeReference<List<AutomationPolicy>>() {
                });
        return response == null ? List.of() : List.copyOf(response);
    }

    private record AccessCheckRequest(String subjectId, String resourceType, UUID resourceId) {
    }

    private record AccessCheckResponse(
            boolean allowed,
            UUID organizationId,
            String organizationRole
    ) {
    }

    public record RecoveryPolicy(
            int reserveMarginPercent,
            int underDeliveryTolerancePercent,
            int underDeliveryGraceSeconds,
            int rebalanceCooldownSeconds
    ) {
    }
}
