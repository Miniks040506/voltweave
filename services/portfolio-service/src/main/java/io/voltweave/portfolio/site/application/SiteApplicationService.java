package io.voltweave.portfolio.site.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.site.application.exception.SiteNotFoundException;
import io.voltweave.portfolio.site.domain.entity.Site;
import io.voltweave.portfolio.site.domain.entity.SitePreference;
import io.voltweave.portfolio.site.persistence.SitePreferenceRepository;
import io.voltweave.portfolio.site.persistence.SiteRepository;

@Service
public class SiteApplicationService {
    private final OrganizationService organizationService;
    private final SiteRepository siteRepository;
    private final SitePreferenceRepository preferenceRepository;

    public SiteApplicationService(
            OrganizationService organizationService,
            SiteRepository siteRepository,
            SitePreferenceRepository preferenceRepository
    ) {
        this.organizationService = organizationService;
        this.siteRepository = siteRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional
    public SiteProfile create(CreateSiteCommand command, String subjectId) {
        organizationService.findForSubject(command.organizationId(), subjectId);
        var now = Instant.now();
        var site = Site.active(
                command.organizationId(), command.name(), command.timezone(),
                command.region(), command.country(), now
        );
        var preference = SitePreference.defaults(command.organizationId(), site.id(), now);

        siteRepository.insert(site);
        preferenceRepository.insert(preference);
        return new SiteProfile(site, preference);
    }

    @Transactional(readOnly = true)
    public SiteProfile findForSubject(UUID siteId, String subjectId) {
        var site = siteRepository.findByIdForSubject(siteId, subjectId)
                .orElseThrow(() -> new SiteNotFoundException(siteId));
        return new SiteProfile(site, preferenceForSubject(siteId, subjectId));
    }

    @Transactional
    public SiteProfile update(
            UUID siteId,
            UpdateSiteCommand command,
            String subjectId
    ) {
        var current = siteRepository.findByIdForSubject(siteId, subjectId)
                .orElseThrow(() -> new SiteNotFoundException(siteId));
        var updated = current.updateDetails(
                command.name(), command.timezone(), command.region(), command.country(),
                Instant.now()
        );
        siteRepository.update(updated);
        return new SiteProfile(updated, preferenceForSubject(siteId, subjectId));
    }

    @Transactional
    public SiteProfile updatePreference(
            UUID siteId,
            UpdateSitePreferenceCommand command,
            String subjectId
    ) {
        var site = siteRepository.findByIdForSubject(siteId, subjectId)
                .orElseThrow(() -> new SiteNotFoundException(siteId));
        var updated = preferenceForSubject(siteId, subjectId).update(
                command.vppOptIn(), command.minimumBatteryReservePercent(), Instant.now()
        );
        preferenceRepository.update(updated);
        return new SiteProfile(site, updated);
    }

    private SitePreference preferenceForSubject(UUID siteId, String subjectId) {
        return preferenceRepository.findBySiteIdForSubject(siteId, subjectId)
                .orElseThrow(() -> new IllegalStateException(
                        "Site preference is missing for site " + siteId
                ));
    }
}
