package io.voltweave.settlement.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.settlement.application.model.RewardLedgerEntry;
import io.voltweave.settlement.persistence.RewardLedgerRepository;

@Service
public class RewardApplicationService {
    private final RewardLedgerRepository repository;

    public RewardApplicationService(RewardLedgerRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<RewardLedgerEntry> earningsForSites(List<UUID> siteIds) {
        return repository.findByParticipantIds(siteIds);
    }
}
