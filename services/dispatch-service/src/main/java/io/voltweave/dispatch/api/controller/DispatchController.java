package io.voltweave.dispatch.api.controller;

import java.net.URI;
import java.time.Duration;
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
import io.voltweave.dispatch.application.DispatchApplicationService;
import io.voltweave.dispatch.application.model.CreateDispatchCommand;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/dispatches")
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class DispatchController {
    private final PortfolioAccessClient accessClient;
    private final DispatchApplicationService dispatchService;

    public DispatchController(
            PortfolioAccessClient accessClient,
            DispatchApplicationService dispatchService
    ) {
        this.accessClient = accessClient;
        this.dispatchService = dispatchService;
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
            UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), dispatch.vppId());
            if (!organizationId.equals(dispatch.organizationId())) {
                throw new AccessDeniedException("Dispatch access denied");
            }
            return DispatchResponse.from(dispatch);
        }));
    }
}
