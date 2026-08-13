package io.voltweave.dispatch.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.dispatch.access.PortfolioAccessClient;
import io.voltweave.dispatch.api.response.AutomationCandidateResponse;
import io.voltweave.dispatch.api.response.DeviceCommandResponse;
import io.voltweave.dispatch.application.AutomationApplicationService;
import io.voltweave.dispatch.application.CommandApplicationService;
import io.voltweave.dispatch.application.DispatchApplicationService;
import io.voltweave.dispatch.http.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/automation-candidates")
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class AutomationCandidateController {
    private final PortfolioAccessClient accessClient;
    private final AutomationApplicationService automationService;
    private final DispatchApplicationService dispatchService;
    private final CommandApplicationService commandService;

    public AutomationCandidateController(
            PortfolioAccessClient accessClient,
            AutomationApplicationService automationService,
            DispatchApplicationService dispatchService,
            CommandApplicationService commandService
    ) {
        this.accessClient = accessClient;
        this.automationService = automationService;
        this.dispatchService = dispatchService;
        this.commandService = commandService;
    }

    @GetMapping
    public List<AutomationCandidateResponse> pending(
            @RequestParam UUID vppId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), vppId);
        return automationService.pendingCandidates(organizationId, vppId).stream()
                .map(AutomationCandidateResponse::from).toList();
    }

    @PostMapping("/{dispatchId}/approve")
    public ResponseEntity<List<DeviceCommandResponse>> approve(
            @PathVariable UUID dispatchId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        var dispatch = dispatchService.find(dispatchId).orElse(null);
        if (dispatch == null) {
            return ResponseEntity.notFound().build();
        }
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), dispatch.vppId());
        if (!organizationId.equals(dispatch.organizationId())) {
            throw new AccessDeniedException("Automation candidate access denied");
        }
        if (!automationService.isPendingCandidate(organizationId, dispatchId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(commandService.prepare(
                dispatchId,
                UUID.fromString(request.getAttribute(CorrelationIdFilter.ATTRIBUTE).toString())
        ).stream().map(DeviceCommandResponse::from).toList());
    }
}
