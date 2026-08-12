package io.voltweave.portfolio.organization.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AuditService auditService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository,
            AuditService auditService
    ) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
        this.auditService = auditService;
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
        auditService.recordUserAction(
                organizationId, actingSubjectId, AuditAction.ORGANIZATION_MEMBER_ADDED,
                AuditResourceType.ORGANIZATION_MEMBER, member.id()
        );
        return member;
    }
}
