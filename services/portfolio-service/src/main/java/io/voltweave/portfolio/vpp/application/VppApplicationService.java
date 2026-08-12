package io.voltweave.portfolio.vpp.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.domain.enums.OrganizationStatus;
import io.voltweave.portfolio.organization.domain.enums.OrganizationType;
import io.voltweave.portfolio.site.persistence.SiteRepository;
import io.voltweave.portfolio.vpp.application.command.CreateVppCommand;
import io.voltweave.portfolio.vpp.application.command.UpdateAutomationPolicyCommand;
import io.voltweave.portfolio.vpp.application.exception.SiteNotVppEligibleException;
import io.voltweave.portfolio.vpp.application.exception.StaleAutomationPolicyException;
import io.voltweave.portfolio.vpp.application.exception.VppMembershipNotFoundException;
import io.voltweave.portfolio.vpp.application.exception.VppNotFoundException;
import io.voltweave.portfolio.vpp.application.model.VppProfile;
import io.voltweave.portfolio.vpp.domain.entities.AutomationPolicy;
import io.voltweave.portfolio.vpp.domain.entities.VirtualPowerPlant;
import io.voltweave.portfolio.vpp.domain.entities.VppInstalledCapacity;
import io.voltweave.portfolio.vpp.domain.entities.VppMembership;
import io.voltweave.portfolio.vpp.domain.enums.VppMembershipStatus;
import io.voltweave.portfolio.vpp.domain.enums.VppStatus;
import io.voltweave.portfolio.vpp.persistence.AutomationPolicyRepository;
import io.voltweave.portfolio.vpp.persistence.VirtualPowerPlantRepository;
import io.voltweave.portfolio.vpp.persistence.VppCapacityRepository;
import io.voltweave.portfolio.vpp.persistence.VppMembershipRepository;

@Service
public class VppApplicationService {
    private final OrganizationService organizationService;
    private final SiteRepository siteRepository;
    private final VirtualPowerPlantRepository vppRepository;
    private final VppMembershipRepository membershipRepository;
    private final AutomationPolicyRepository policyRepository;
    private final VppCapacityRepository capacityRepository;

    public VppApplicationService(
            OrganizationService organizationService,
            SiteRepository siteRepository,
            VirtualPowerPlantRepository vppRepository,
            VppMembershipRepository membershipRepository,
            AutomationPolicyRepository policyRepository,
            VppCapacityRepository capacityRepository
    ) {
        this.organizationService = organizationService;
        this.siteRepository = siteRepository;
        this.vppRepository = vppRepository;
        this.membershipRepository = membershipRepository;
        this.policyRepository = policyRepository;
        this.capacityRepository = capacityRepository;
    }

    @Transactional
    public VppProfile create(CreateVppCommand command, String subjectId) {
        var organization = organizationService.findForSubject(
                command.organizationId(), subjectId
        );
        if (organization.type() != OrganizationType.VPP_OPERATOR
                || organization.status() != OrganizationStatus.ACTIVE) {
            throw new IllegalArgumentException(
                    "VPP requires an active VPP_OPERATOR organization"
            );
        }
        var now = Instant.now();
        var vpp = VirtualPowerPlant.active(
                organization.id(), command.name(), command.region(), now
        );
        var policy = AutomationPolicy.disabledDefaults(vpp, now);
        vppRepository.insert(vpp);
        policyRepository.insert(policy);
        return new VppProfile(vpp, policy, java.util.List.of());
    }

    @Transactional(readOnly = true)
    public VppProfile findForSubject(UUID vppId, String subjectId) {
        return profile(requireVpp(vppId, subjectId));
    }

    @Transactional
    public VppProfile addSite(UUID vppId, UUID siteId, String subjectId) {
        var vpp = requireActiveVpp(vppId, subjectId);
        var site = siteRepository.findVppEligibleById(siteId)
                .orElseThrow(() -> new SiteNotVppEligibleException(siteId));
        var existing = membershipRepository.find(vpp.organizationId(), vpp.id(), siteId);
        if (existing.isPresent()
                && existing.orElseThrow().status() == VppMembershipStatus.ACTIVE) {
            return profile(vpp);
        }

        var now = Instant.now();
        if (existing.isPresent()) {
            membershipRepository.update(existing.orElseThrow().activate(now));
        } else {
            membershipRepository.insert(VppMembership.active(
                    vpp, site.organizationId(), site.id(), now
            ));
        }
        return profile(vpp);
    }

    @Transactional
    public VppProfile removeSite(UUID vppId, UUID siteId, String subjectId) {
        var vpp = requireVpp(vppId, subjectId);
        var membership = membershipRepository.find(vpp.organizationId(), vpp.id(), siteId)
                .filter(current -> current.status() == VppMembershipStatus.ACTIVE)
                .orElseThrow(() -> new VppMembershipNotFoundException(siteId));
        membershipRepository.update(membership.remove(Instant.now()));
        return profile(vpp);
    }

    @Transactional
    public VppProfile updatePolicy(
            UUID vppId,
            UpdateAutomationPolicyCommand command,
            String subjectId
    ) {
        var vpp = requireActiveVpp(vppId, subjectId);
        var current = currentPolicy(vpp);
        if (command.expectedVersion() != current.version()) {
            throw new StaleAutomationPolicyException(
                    command.expectedVersion(), current.version()
            );
        }
        var updated = current.update(
                command.enabled(), command.triggerType(), command.approvalMode(),
                command.peakImportLimitKw(), command.priceThreshold(),
                command.reserveMarginPercent(), command.maxDispatchPowerKw(),
                command.maxDispatchDurationMinutes(),
                command.underDeliveryTolerancePercent(),
                command.underDeliveryGraceSeconds(), command.rebalanceCooldownSeconds(),
                command.effectiveFrom(), Instant.now()
        );
        policyRepository.insert(updated);
        return profile(vpp);
    }

    @Transactional(readOnly = true)
    public VppInstalledCapacity installedCapacity(UUID vppId, String subjectId) {
        var vpp = requireVpp(vppId, subjectId);
        return capacityRepository.calculate(vpp.organizationId(), vpp.id());
    }

    private VirtualPowerPlant requireVpp(UUID vppId, String subjectId) {
        return vppRepository.findByIdForSubject(vppId, subjectId)
                .orElseThrow(() -> new VppNotFoundException(vppId));
    }

    private VirtualPowerPlant requireActiveVpp(UUID vppId, String subjectId) {
        var vpp = requireVpp(vppId, subjectId);
        if (vpp.status() != VppStatus.ACTIVE) {
            throw new IllegalArgumentException("VPP must be active");
        }
        return vpp;
    }

    private VppProfile profile(VirtualPowerPlant vpp) {
        return new VppProfile(
                vpp,
                currentPolicy(vpp),
                membershipRepository.findActiveByVpp(vpp.organizationId(), vpp.id())
        );
    }

    private AutomationPolicy currentPolicy(VirtualPowerPlant vpp) {
        return policyRepository.findCurrent(vpp.organizationId(), vpp.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Automation policy is missing for VPP " + vpp.id()
                ));
    }
}
