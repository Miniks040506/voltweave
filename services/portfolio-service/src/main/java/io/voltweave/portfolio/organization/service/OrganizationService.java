package io.voltweave.portfolio.organization.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.organization.domain.Organization;
import io.voltweave.portfolio.organization.domain.OrganizationMember;
import io.voltweave.portfolio.organization.domain.OrganizationRole;
import io.voltweave.portfolio.organization.repository.OrganizationMemberRepository;
import io.voltweave.portfolio.organization.repository.OrganizationRepository;

@Service
public class OrganizationService {
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository memberRepository
    ) {
        this.organizationRepository = organizationRepository;
        this.memberRepository = memberRepository;
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
        return member;
    }
}
