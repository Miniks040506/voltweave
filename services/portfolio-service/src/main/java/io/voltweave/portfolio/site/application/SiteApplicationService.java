package io.voltweave.portfolio.site.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.contracts.events.EventTypes;
import io.voltweave.contracts.events.portfolio.v1.PortfolioChangeTypeV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioLifecyclePayloadV1;
import io.voltweave.contracts.events.portfolio.v1.PortfolioResourceTypeV1;
import io.voltweave.portfolio.audit.application.AuditService;
import io.voltweave.portfolio.audit.domain.enums.AuditAction;
import io.voltweave.portfolio.audit.domain.enums.AuditResourceType;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.site.application.command.CreateSiteCommand;
import io.voltweave.portfolio.site.application.command.UpdateSiteCommand;
import io.voltweave.portfolio.site.application.command.UpdateSitePreferenceCommand;
import io.voltweave.portfolio.site.application.exception.SiteNotFoundException;
import io.voltweave.portfolio.site.application.model.SiteProfile;
import io.voltweave.portfolio.site.domain.entities.Site;
import io.voltweave.portfolio.site.domain.entities.SitePreference;
import io.voltweave.portfolio.site.persistence.SitePreferenceRepository;
import io.voltweave.portfolio.site.persistence.SiteRepository;
import io.voltweave.portfolio.messaging.application.PortfolioEventService;

@Service
public class SiteApplicationService {
    private final OrganizationService organizationService;
    private final SiteRepository siteRepository;
    private final SitePreferenceRepository preferenceRepository;
    private final AuditService auditService;
    private final PortfolioEventService eventService;

    public SiteApplicationService(
            OrganizationService organizationService,
            SiteRepository siteRepository,
            SitePreferenceRepository preferenceRepository,
            AuditService auditService,
            PortfolioEventService eventService
    ) {
        this.organizationService = organizationService;
        this.siteRepository = siteRepository;
        this.preferenceRepository = preferenceRepository;
        this.auditService = auditService;
        this.eventService = eventService;
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
        var audit = auditService.recordUserAction(
                site.organizationId(), subjectId, AuditAction.SITE_PREFERENCE_UPDATED,
                AuditResourceType.SITE, site.id()
        );
        eventService.record(
                site.organizationId(), EventTypes.SITE_PREFERENCE_UPDATED, site.id(),
                new PortfolioLifecyclePayloadV1(
                        site.id(), PortfolioResourceTypeV1.SITE_PREFERENCE,
                        PortfolioChangeTypeV1.UPDATED, null
                ), audit.correlationId()
        );
        return new SiteProfile(site, updated);
    }

    private SitePreference preferenceForSubject(UUID siteId, String subjectId) {
        return preferenceRepository.findBySiteIdForSubject(siteId, subjectId)
                .orElseThrow(() -> new IllegalStateException(
                        "Site preference is missing for site " + siteId
                ));
    }
}
