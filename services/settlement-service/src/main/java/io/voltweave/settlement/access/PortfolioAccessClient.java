package io.voltweave.settlement.access;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PortfolioAccessClient {
    private final RestClient restClient;

    public PortfolioAccessClient(RestClient settlementPortfolioRestClient) {
        this.restClient = settlementPortfolioRestClient;
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

    public List<UUID> siteIdsForSubject(String subjectId) {
        var response = restClient.post().uri("/internal/v1/access-checks/sites")
                .body(new SubjectSitesRequest(subjectId))
                .retrieve().body(SubjectSitesResponse.class);
        return response == null ? List.of() : response.siteIds();
    }

    private record AccessCheckRequest(String subjectId, String resourceType, UUID resourceId) {
    }

    private record AccessCheckResponse(
            boolean allowed,
            UUID organizationId,
            String organizationRole
    ) {
    }

    private record SubjectSitesRequest(String subjectId) {
    }

    private record SubjectSitesResponse(List<UUID> siteIds) {
    }
}
