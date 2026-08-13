package io.voltweave.dispatch.api.response;

import java.time.Instant;
import java.util.UUID;

import io.voltweave.dispatch.application.model.AutomationCandidate;

public record AutomationCandidateResponse(
        UUID policyId,
        int policyVersion,
        Instant evaluatedAt,
        DispatchResponse dispatch
) {
    public static AutomationCandidateResponse from(AutomationCandidate candidate) {
        return new AutomationCandidateResponse(
                candidate.policyId(), candidate.policyVersion(), candidate.evaluatedAt(),
                DispatchResponse.from(candidate.dispatch())
        );
    }
}
