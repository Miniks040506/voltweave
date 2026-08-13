package io.voltweave.dispatch.api.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.voltweave.dispatch.application.model.Dispatch;

public record DispatchResponse(
        UUID id, UUID vppId, UUID optimizationPreviewId, long optimizationPreviewVersion,
        String type, BigDecimal targetPowerKw, BigDecimal requiredPowerKw,
        BigDecimal plannedPowerKw, Instant scheduledStartAt, Instant scheduledEndAt,
        String status, String createdBy, Instant createdAt, long version,
        BaselineResponse baseline, List<AllocationResponse> allocations
) {
    public static DispatchResponse from(Dispatch dispatch) {
        return new DispatchResponse(
                dispatch.id(), dispatch.vppId(), dispatch.optimizationPreviewId(),
                dispatch.optimizationPreviewVersion(), dispatch.type(), dispatch.targetPowerKw(),
                dispatch.requiredPowerKw(), dispatch.plannedPowerKw(), dispatch.scheduledStartAt(),
                dispatch.scheduledEndAt(), dispatch.status().name(), dispatch.createdBy(),
                dispatch.createdAt(), dispatch.version(), BaselineResponse.from(dispatch.baseline()),
                dispatch.allocations().stream().map(AllocationResponse::from).toList()
        );
    }

    public record AllocationResponse(
            UUID siteId, UUID deviceId, String deviceType,
            BigDecimal allocatedPowerKw, BigDecimal expectedEnergyKwh, BigDecimal score
    ) {
        static AllocationResponse from(Dispatch.Allocation value) {
            return new AllocationResponse(
                    value.siteId(), value.deviceId(), value.deviceType(), value.allocatedPowerKw(),
                    value.expectedEnergyKwh(), value.score()
            );
        }
    }

    public record BaselineResponse(
            UUID forecastId, long forecastVersion, String modelName, String modelVersion,
            Instant sourceValidUntil, Instant frozenAt, List<BaselinePointResponse> points
    ) {
        static BaselineResponse from(Dispatch.Baseline value) {
            return new BaselineResponse(
                    value.forecastId(), value.forecastVersion(), value.modelName(),
                    value.modelVersion(), value.sourceValidUntil(), value.frozenAt(),
                    value.points().stream().map(BaselinePointResponse::from).toList()
            );
        }
    }

    public record BaselinePointResponse(Instant forecastAt, BigDecimal baselineGridImportKw) {
        static BaselinePointResponse from(Dispatch.BaselinePoint value) {
            return new BaselinePointResponse(value.forecastAt(), value.baselineGridImportKw());
        }
    }
}
