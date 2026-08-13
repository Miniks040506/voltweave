package io.voltweave.dispatch.application.model;

import java.time.Instant;
import java.util.UUID;

public record AutomationCandidate(
        UUID policyId,
        int policyVersion,
        Instant evaluatedAt,
        Dispatch dispatch
) {
}
