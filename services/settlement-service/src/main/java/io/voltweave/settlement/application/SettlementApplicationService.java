package io.voltweave.settlement.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.dispatch.v1.DispatchCompletedPayloadV1;
import io.voltweave.settlement.access.DispatchSettlementClient;
import io.voltweave.settlement.access.DispatchSettlementClient.SettlementInput;
import io.voltweave.settlement.application.model.Settlement;
import io.voltweave.settlement.persistence.SettlementRepository;

@Service
public class SettlementApplicationService {
    private final DispatchSettlementClient dispatchClient;
    private final SettlementRepository repository;

    public SettlementApplicationService(
            DispatchSettlementClient dispatchClient,
            SettlementRepository repository
    ) {
        this.dispatchClient = dispatchClient;
        this.repository = repository;
    }

    @Transactional
    public void calculate(
            UUID eventId,
            UUID organizationId,
            DispatchCompletedPayloadV1 completion,
            Instant receivedAt
    ) {
        if (!repository.recordEventIfNew(
                eventId, EventTypes.DISPATCH_COMPLETED, receivedAt
        )) {
            return;
        }
        SettlementInput input = dispatchClient.input(completion.dispatchId());
        validate(organizationId, completion, input);

        BigDecimal expected = completion.targetPowerKw()
                .multiply(BigDecimal.valueOf(Duration.between(
                        completion.scheduledStartAt(), completion.scheduledEndAt()
                ).toMillis()))
                .divide(BigDecimal.valueOf(3_600_000), 6, RoundingMode.HALF_UP);
        BigDecimal delivered = sum(input.participants().stream()
                .map(DispatchSettlementClient.Participant::deliveredEnergyKwh).toList());
        var lines = input.participants().stream().map(participant -> new Settlement.Line(
                participant.siteId(), participant.deviceId(), participant.deviceType(),
                participant.requestedPowerKw(), participant.expectedEnergyKwh(),
                participant.deliveredEnergyKwh(), percent(
                        participant.deliveredEnergyKwh(), participant.expectedEnergyKwh()
                )
        )).toList();
        var baselinePoints = input.baselinePoints().stream()
                .map(point -> new Settlement.BaselinePoint(
                        point.forecastAt(), point.baselineGridImportKw()
                )).toList();
        repository.insert(new Settlement(
                UUID.randomUUID(), organizationId, input.dispatchId(), input.vppId(),
                input.completionStatus(), input.targetPowerKw(), input.scheduledStartAt(),
                input.scheduledEndAt(), input.frozenAt(), input.baselineId(),
                input.baselineVersion(), input.baselineModelName(), input.baselineModelVersion(),
                expected, delivered, percent(delivered, expected), "CALCULATED",
                completion.completedAt(),
                baselinePoints, lines
        ));
    }

    private static void validate(
            UUID organizationId,
            DispatchCompletedPayloadV1 completion,
            SettlementInput input
    ) {
        if (!organizationId.equals(input.organizationId())
                || !completion.dispatchId().equals(input.dispatchId())
                || !completion.vppId().equals(input.vppId())
                || !completion.completionStatus().equals(input.completionStatus())
                || completion.targetPowerKw().compareTo(input.targetPowerKw()) != 0
                || !completion.baselineId().equals(input.baselineId())
                || completion.baselineVersion() != input.baselineVersion()
                || !completion.scheduledStartAt().equals(input.scheduledStartAt())
                || !completion.scheduledEndAt().equals(input.scheduledEndAt())) {
            throw new IllegalArgumentException("Dispatch event does not match settlement input");
        }
        BigDecimal delivered = sum(input.participants().stream()
                .map(DispatchSettlementClient.Participant::deliveredEnergyKwh).toList());
        if (delivered.compareTo(completion.deliveredEnergyKwh()) != 0) {
            throw new IllegalArgumentException("Delivered energy does not match completion event");
        }
    }

    private static BigDecimal percent(BigDecimal value, BigDecimal expected) {
        return value.multiply(BigDecimal.valueOf(100))
                .divide(expected, 3, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(java.util.List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(6, RoundingMode.HALF_UP);
    }
}
