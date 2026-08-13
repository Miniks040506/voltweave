package io.voltweave.portfolio.device.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.device.application.model.FlexibilityResource;
import io.voltweave.portfolio.device.persistence.FlexibilityResourceRepository;

@Service
public class FlexibilityResourceQueryService {
    private final FlexibilityResourceRepository repository;

    public FlexibilityResourceQueryService(FlexibilityResourceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<FlexibilityResource> listForVpp(UUID vppId) {
        return repository.findByVppId(vppId);
    }
}
