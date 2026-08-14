package io.voltweave.portfolio.organization.application;

import java.time.Instant;
import java.util.List;
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
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import io.voltweave.portfolio.organization.application.exception.OrganizationNotFoundException;
import io.voltweave.portfolio.organization.domain.entities.Organization;
import io.voltweave.portfolio.organization.domain.entities.OrganizationMember;
import io.voltweave.portfolio.organization.domain.enums.OrganizationRole;
import io.voltweave.portfolio.organization.persistence.OrganizationMemberRepository;
import io.voltweave.portfolio.organization.persistence.OrganizationRepository;
import io.voltweave.portfolio.messaging.application.PortfolioEventService;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AuditService auditService;
    private final PortfolioEventService eventService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            AuditService auditService,
            PortfolioEventService eventService
    ) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.auditService = auditService;
        this.eventService = eventService;
    }

    @Transactional
    public Organization create(CreateOrganizationCommand command, String ownerSubjectId) {
        var now = Instant.now();
        var organization = Organization.active(
                command.type(),
                command.legalName(),
                command.displayName(),
                command.tenantCode(),
                command.country(),
                command.timezone(),
                now
        );
        var owner = OrganizationMember.active(
                organization.id(), ownerSubjectId, OrganizationRole.OWNER, now
        );

        organizationRepository.insert(organization);
        memberRepository.insert(owner);
        return organization;
    }

    public Organization findForSubject(UUID organizationId, String subjectId) {
        return organizationRepository.findByIdForSubject(organizationId, subjectId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    @Transactional(readOnly = true)
    public List<Organization> findAllForSubject(String subjectId) {
        return organizationRepository.findAllForSubject(subjectId);
    }

    @Transactional
    public OrganizationMember addMember(
            UUID organizationId,
            String actingSubjectId,
            String subjectId,
            OrganizationRole role
    ) {
        findForSubject(organizationId, actingSubjectId);
        var member = OrganizationMember.active(organizationId, subjectId, role, Instant.now());
        memberRepository.insert(member);
        var audit = auditService.recordUserAction(
                organizationId, actingSubjectId, AuditAction.ORGANIZATION_MEMBER_ADDED,
                AuditResourceType.ORGANIZATION_MEMBER, member.id()
        );
        eventService.record(
                organizationId, EventTypes.ORGANIZATION_MEMBER_ADDED, organizationId,
                new PortfolioLifecyclePayloadV1(
                        member.id(), PortfolioResourceTypeV1.ORGANIZATION_MEMBER,
                        PortfolioChangeTypeV1.ADDED, organizationId
                ), audit.correlationId()
        );
        return member;
    }
}
