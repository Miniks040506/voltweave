package io.voltweave.portfolio.access.application.command;

import java.util.UUID;

import io.voltweave.portfolio.access.domain.enums.AccessResourceType;

public record CheckAccessCommand(
        String subjectId,
        AccessResourceType resourceType,
        UUID resourceId
) {
}
