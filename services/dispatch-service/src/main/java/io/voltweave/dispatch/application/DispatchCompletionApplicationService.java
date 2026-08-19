package io.voltweave.dispatch.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTopics;
import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.dispatch.v1.DispatchCompletedPayloadV1;
import io.voltweave.contracts.events.v1.EventEnvelopeV1;
import io.voltweave.dispatch.domain.entities.DispatchState;
import io.voltweave.dispatch.domain.enums.DispatchStatus;
import io.voltweave.dispatch.persistence.CompletionRepository;
import io.voltweave.dispatch.persistence.DispatchRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class DispatchCompletionApplicationService {
    private final CompletionRepository completionRepository;
    private final DispatchRepository dispatchRepository;
    private final ObjectMapper objectMapper;

    public DispatchCompletionApplicationService(
            CompletionRepository completionRepository,
            DispatchRepository dispatchRepository,
            ObjectMapper objectMapper
    ) {
        this.completionRepository = completionRepository;
        this.dispatchRepository = dispatchRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void complete(UUID dispatchId, Instant completedAt) {
        if (!completionRepository.lock(dispatchId)) {
            return;
        }
        var dispatch = dispatchRepository.findById(dispatchId).orElseThrow();
        if (dispatch.scheduledEndAt().isAfter(completedAt)
                || dispatch.status() != DispatchStatus.ACTIVE
                && dispatch.status() != DispatchStatus.REBALANCING) {
            return;
        }

        var completing = new DispatchState(dispatch.status())
                .transitionTo(DispatchStatus.COMPLETING).status();
        completionRepository.transition(dispatchId, dispatch.status(), completing);
        BigDecimal delivered = completionRepository.deliveredEnergy(dispatchId);
        BigDecimal expected = expectedEnergy(
                dispatch.targetPowerKw(), dispatch.scheduledStartAt(), dispatch.scheduledEndAt()
        );
        DispatchStatus completed = delivered.compareTo(expected) >= 0
                ? DispatchStatus.COMPLETED : DispatchStatus.PARTIALLY_COMPLETED;
        new DispatchState(completing).transitionTo(completed);
        completionRepository.transition(dispatchId, completing, completed);

        var payload = new DispatchCompletedPayloadV1(
                dispatch.id(), dispatch.vppId(), completed.name(), dispatch.targetPowerKw(),
                delivered, dispatch.baseline().forecastId(),
                dispatch.baseline().forecastVersion(), dispatch.scheduledStartAt(),
                dispatch.scheduledEndAt(), completedAt
        );
        var event = EventEnvelopeV1.create(
                EventTypes.DISPATCH_COMPLETED, "dispatch-service",
                dispatch.organizationId(), dispatch.id(), null,
                dispatch.id().toString(), payload, completedAt
        );
        completionRepository.insertOutbox(
                event.eventId(), dispatch.id(), EventTopics.DISPATCH_LIFECYCLE_V1,
                serialize(event), completedAt
        );
    }

    private static BigDecimal expectedEnergy(
            BigDecimal targetPowerKw,
            Instant startAt,
            Instant endAt
    ) {
        return targetPowerKw.multiply(BigDecimal.valueOf(
                        Duration.between(startAt, endAt).toMillis()
                ))
                .divide(BigDecimal.valueOf(3_600_000), 3, RoundingMode.HALF_UP);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Cannot serialize dispatch completion event", exception);
        }
    }
}
