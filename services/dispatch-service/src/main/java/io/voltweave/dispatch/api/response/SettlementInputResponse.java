package io.voltweave.dispatch.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.dispatch.application.model.SettlementInput;

public record SettlementInputResponse(
        UUID organizationId,
        UUID dispatchId,
        UUID vppId,
        String completionStatus,
        BigDecimal targetPowerKw,
        Instant scheduledStartAt,
        Instant scheduledEndAt,
        Instant frozenAt,
        UUID baselineId,
        long baselineVersion,
        String baselineModelName,
        String baselineModelVersion,
        List<SettlementInput.BaselinePoint> baselinePoints,
        List<SettlementInput.Participant> participants
) {
    public static SettlementInputResponse from(SettlementInput input) {
        return new SettlementInputResponse(
                input.organizationId(), input.dispatchId(), input.vppId(),
                input.completionStatus(), input.targetPowerKw(), input.scheduledStartAt(),
                input.scheduledEndAt(), input.frozenAt(), input.baselineId(),
                input.baselineVersion(), input.baselineModelName(),
                input.baselineModelVersion(), input.baselinePoints(), input.participants()
        );
    }
}
