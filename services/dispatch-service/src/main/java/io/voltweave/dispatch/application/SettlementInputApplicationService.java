package io.voltweave.dispatch.application;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.dispatch.application.model.SettlementInput;
import io.voltweave.dispatch.domain.enums.DispatchStatus;
import io.voltweave.dispatch.persistence.DispatchRepository;
import io.voltweave.dispatch.persistence.SettlementInputRepository;

@Service
public class SettlementInputApplicationService {
    private final DispatchRepository dispatchRepository;
    private final SettlementInputRepository settlementInputRepository;

    public SettlementInputApplicationService(
            DispatchRepository dispatchRepository,
            SettlementInputRepository settlementInputRepository
    ) {
        this.dispatchRepository = dispatchRepository;
        this.settlementInputRepository = settlementInputRepository;
    }

    @Transactional(readOnly = true)
    public Optional<SettlementInput> find(UUID dispatchId) {
        return dispatchRepository.findById(dispatchId)
                .filter(dispatch -> dispatch.status() == DispatchStatus.COMPLETED
                        || dispatch.status() == DispatchStatus.PARTIALLY_COMPLETED)
                .map(dispatch -> new SettlementInput(
                        dispatch.organizationId(), dispatch.id(), dispatch.vppId(),
                        dispatch.status().name(), dispatch.targetPowerKw(),
                        dispatch.scheduledStartAt(), dispatch.scheduledEndAt(),
                        dispatch.baseline().frozenAt(), dispatch.baseline().forecastId(),
                        dispatch.baseline().forecastVersion(), dispatch.baseline().modelName(),
                        dispatch.baseline().modelVersion(),
                        dispatch.baseline().points().stream()
                                .map(point -> new SettlementInput.BaselinePoint(
                                        point.forecastAt(), point.baselineGridImportKw()
                                )).toList(),
                        settlementInputRepository.participants(dispatch.id())
                ));
    }
}
