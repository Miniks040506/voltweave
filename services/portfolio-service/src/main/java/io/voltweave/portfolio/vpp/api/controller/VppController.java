package io.voltweave.portfolio.vpp.api.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.vpp.api.request.CreateVppRequest;
import io.voltweave.portfolio.vpp.api.request.UpdateAutomationPolicyRequest;
import io.voltweave.portfolio.vpp.api.response.VppCapacityResponse;
import io.voltweave.portfolio.vpp.api.response.VppResponse;
import io.voltweave.portfolio.vpp.application.VppApplicationService;
import io.voltweave.portfolio.vpp.application.command.CreateVppCommand;
import io.voltweave.portfolio.vpp.application.command.UpdateAutomationPolicyCommand;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vpps")
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class VppController {
    private final VppApplicationService vppService;

    public VppController(VppApplicationService vppService) {
        this.vppService = vppService;
    }

    @PostMapping
    public ResponseEntity<VppResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateVppRequest request
    ) {
        var profile = vppService.create(
                new CreateVppCommand(
                        request.organizationId(), request.name(), request.region()
                ),
                jwt.getSubject()
        );
        return ResponseEntity.created(URI.create("/api/v1/vpps/" + profile.vpp().id()))
                .body(VppResponse.from(profile));
    }

    @GetMapping("/{vppId}")
    public VppResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vppId
    ) {
        return VppResponse.from(vppService.findForSubject(vppId, jwt.getSubject()));
    }

    @PostMapping("/{vppId}/sites/{siteId}")
    public ResponseEntity<VppResponse> addSite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vppId,
            @PathVariable UUID siteId
    ) {
        var profile = vppService.addSite(vppId, siteId, jwt.getSubject());
        return ResponseEntity.created(URI.create(
                "/api/v1/vpps/" + vppId + "/sites/" + siteId
        )).body(VppResponse.from(profile));
    }

    @DeleteMapping("/{vppId}/sites/{siteId}")
    public VppResponse removeSite(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vppId,
            @PathVariable UUID siteId
    ) {
        return VppResponse.from(vppService.removeSite(vppId, siteId, jwt.getSubject()));
    }

    @GetMapping("/{vppId}/capacity")
    public VppCapacityResponse capacity(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vppId
    ) {
        return VppCapacityResponse.from(
                vppId, vppService.installedCapacity(vppId, jwt.getSubject())
        );
    }

    @PutMapping("/{vppId}/automation-policy")
    public VppResponse updateAutomationPolicy(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID vppId,
            @Valid @RequestBody UpdateAutomationPolicyRequest request
    ) {
        return VppResponse.from(vppService.updatePolicy(
                vppId,
                new UpdateAutomationPolicyCommand(
                        request.expectedVersion(), request.enabled(), request.triggerType(),
                        request.approvalMode(), request.peakImportLimitKw(),
                        request.priceThreshold(), request.reserveMarginPercent(),
                        request.maxDispatchPowerKw(), request.maxDispatchDurationMinutes(),
                        request.underDeliveryTolerancePercent(),
                        request.underDeliveryGraceSeconds(),
                        request.rebalanceCooldownSeconds(), request.effectiveFrom()
                ),
                jwt.getSubject()
        ));
    }
}
