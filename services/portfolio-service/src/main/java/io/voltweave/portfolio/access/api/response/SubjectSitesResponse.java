package io.voltweave.portfolio.access.api.response;

import java.util.List;
import java.util.UUID;

public record SubjectSitesResponse(List<UUID> siteIds) {
    public SubjectSitesResponse {
        siteIds = List.copyOf(siteIds);
    }
}
