package io.voltweave.intelligence.access;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PortfolioAccessClient {
    private final RestClient restClient;

    public PortfolioAccessClient(RestClient intelligencePortfolioRestClient) {
        this.restClient = intelligencePortfolioRestClient;
    }

    public UUID requireVppAccess(String subjectId, UUID vppId) {
        var response = restClient.post()
                .uri("/internal/v1/access-checks")
                .body(new AccessCheckRequest(subjectId, "VPP", vppId))
                .retrieve()
                .body(AccessCheckResponse.class);
        if (response == null || !response.allowed() || response.organizationId() == null) {
            throw new AccessDeniedException("VPP access denied");
        }
        return response.organizationId();
    }

    private record AccessCheckRequest(
            String subjectId,
            String resourceType,
            UUID resourceId
    ) {
    }

    private record AccessCheckResponse(
            boolean allowed,
            UUID organizationId,
            String organizationRole
    ) {
    }
}
