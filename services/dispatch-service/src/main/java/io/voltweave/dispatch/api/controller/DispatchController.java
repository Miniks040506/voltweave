package io.voltweave.dispatch.api.controller;

import java.net.URI;
import java.time.Duration;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.dispatch.access.PortfolioAccessClient;
import io.voltweave.dispatch.api.request.CreateDispatchRequest;
import io.voltweave.dispatch.api.response.DispatchResponse;
import io.voltweave.dispatch.api.response.DeviceCommandResponse;
import io.voltweave.dispatch.api.response.DispatchPerformanceResponse;
import io.voltweave.dispatch.application.CommandApplicationService;
import io.voltweave.dispatch.application.DispatchApplicationService;
import io.voltweave.dispatch.application.PerformanceApplicationService;
import io.voltweave.dispatch.application.model.Dispatch;
import io.voltweave.dispatch.application.model.CreateDispatchCommand;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import io.voltweave.dispatch.http.CorrelationIdFilter;

@RestController
@RequestMapping("/api/v1/dispatches")
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class DispatchController {
    private final PortfolioAccessClient accessClient;
    private final DispatchApplicationService dispatchService;
    private final CommandApplicationService commandService;
    private final PerformanceApplicationService performanceService;

    public DispatchController(
            PortfolioAccessClient accessClient,
            DispatchApplicationService dispatchService,
            CommandApplicationService commandService,
            PerformanceApplicationService performanceService
    ) {
        this.accessClient = accessClient;
        this.dispatchService = dispatchService;
        this.commandService = commandService;
        this.performanceService = performanceService;
    }

    @GetMapping("/{dispatchId}/performance")
    public ResponseEntity<DispatchPerformanceResponse> performance(
            @PathVariable UUID dispatchId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Dispatch dispatch = dispatchService.find(dispatchId).orElse(null);
        if (dispatch == null) {
            return ResponseEntity.notFound().build();
        }
        requireAccess(jwt, dispatch);
        return ResponseEntity.ok(DispatchPerformanceResponse.from(
                performanceService.get(dispatch)
        ));
    }

    @PostMapping
    public ResponseEntity<DispatchResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateDispatchRequest request
    ) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), request.vppId());
        var dispatch = dispatchService.create(new CreateDispatchCommand(
                organizationId, request.vppId(), request.optimizationPreviewId(), request.type(),
                request.scheduledStartAt(), Duration.ofMinutes(request.durationMinutes()),
                jwt.getSubject(), idempotencyKey
        ));
        return ResponseEntity.created(URI.create("/api/v1/dispatches/" + dispatch.id()))
                .body(DispatchResponse.from(dispatch));
    }

    @GetMapping("/{dispatchId}")
    public ResponseEntity<DispatchResponse> get(
            @PathVariable UUID dispatchId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.of(dispatchService.find(dispatchId).map(dispatch -> {
            requireAccess(jwt, dispatch);
            return DispatchResponse.from(dispatch);
        }));
    }

    @PostMapping("/{dispatchId}/commands")
    public ResponseEntity<List<DeviceCommandResponse>> prepareCommands(
            @PathVariable UUID dispatchId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request
    ) {
        Dispatch dispatch = dispatchService.find(dispatchId).orElse(null);
        if (dispatch == null) {
            return ResponseEntity.notFound().build();
        }
        requireAccess(jwt, dispatch);
        var commands = commandService.prepare(
                dispatchId,
                UUID.fromString(request.getAttribute(CorrelationIdFilter.ATTRIBUTE).toString())
        )
                .stream().map(DeviceCommandResponse::from).toList();
        return ResponseEntity.ok(commands);
    }

    private void requireAccess(Jwt jwt, Dispatch dispatch) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), dispatch.vppId());
        if (!organizationId.equals(dispatch.organizationId())) {
            throw new AccessDeniedException("Dispatch access denied");
        }
    }
}
