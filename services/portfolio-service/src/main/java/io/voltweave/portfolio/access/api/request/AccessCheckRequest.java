package io.voltweave.portfolio.access.api.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import io.voltweave.portfolio.access.application.command.CheckAccessCommand;
import io.voltweave.portfolio.access.domain.enums.AccessResourceType;

public record AccessCheckRequest(
        @NotBlank @Size(max = 255) String subjectId,
        @NotNull AccessResourceType resourceType,
        @NotNull UUID resourceId
) {
    public CheckAccessCommand toCommand() {
        return new CheckAccessCommand(subjectId, resourceType, resourceId);
    }
}
