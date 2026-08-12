package io.voltweave.telemetry.access;

import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PortfolioAccessClient {
    private final RestClient restClient;

    public PortfolioAccessClient(RestClient portfolioAccessRestClient) {
        this.restClient = portfolioAccessRestClient;
    }

    public UUID requireSiteAccess(String subjectId, UUID siteId) {
        return requireAccess(subjectId, "SITE", siteId);
    }

    public UUID requireDeviceAccess(String subjectId, UUID deviceId) {
        return requireAccess(subjectId, "DEVICE", deviceId);
    }

    private UUID requireAccess(String subjectId, String resourceType, UUID resourceId) {
        var response = restClient.post()
                .uri("/internal/v1/access-checks")
                .body(new AccessCheckRequest(subjectId, resourceType, resourceId))
                .retrieve()
                .body(AccessCheckResponse.class);
        if (response == null || !response.allowed() || response.organizationId() == null) {
            throw new AccessDeniedException("Resource access denied");
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
