package io.voltweave.contracts.events.portfolio.v1;

import java.util.Objects;
import java.util.UUID;

public record PortfolioLifecyclePayloadV1(
        UUID resourceId,
        PortfolioResourceTypeV1 resourceType,
        PortfolioChangeTypeV1 changeType,
        UUID relatedResourceId
) {
    public PortfolioLifecyclePayloadV1 {
        Objects.requireNonNull(resourceId, "resourceId is required");
        Objects.requireNonNull(resourceType, "resourceType is required");
        Objects.requireNonNull(changeType, "changeType is required");
    }
}
