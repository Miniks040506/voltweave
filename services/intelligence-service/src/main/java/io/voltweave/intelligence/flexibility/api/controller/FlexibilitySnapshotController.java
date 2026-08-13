package io.voltweave.intelligence.flexibility.api.controller;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.intelligence.access.PortfolioAccessClient;
import io.voltweave.intelligence.flexibility.api.request.GenerateFlexibilitySnapshotRequest;
import io.voltweave.intelligence.flexibility.api.response.FlexibilitySnapshotResponse;
import io.voltweave.intelligence.flexibility.application.FlexibilitySnapshotApplicationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vpps/{vppId}/flexibility")
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class FlexibilitySnapshotController {
    private final PortfolioAccessClient accessClient;
    private final FlexibilitySnapshotApplicationService snapshotService;

    public FlexibilitySnapshotController(
            PortfolioAccessClient accessClient,
            FlexibilitySnapshotApplicationService snapshotService
    ) {
        this.accessClient = accessClient;
        this.snapshotService = snapshotService;
    }

    @PostMapping
    public ResponseEntity<FlexibilitySnapshotResponse> generate(
            @PathVariable UUID vppId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateFlexibilitySnapshotRequest request
    ) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), vppId);
        var snapshot = snapshotService.generate(
                organizationId, vppId, Duration.ofMinutes(request.dispatchDurationMinutes())
        );
        return ResponseEntity.created(URI.create(
                "/api/v1/vpps/" + vppId + "/flexibility"
        )).body(FlexibilitySnapshotResponse.from(snapshot));
    }

    @GetMapping
    public ResponseEntity<FlexibilitySnapshotResponse> latest(
            @PathVariable UUID vppId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), vppId);
        return ResponseEntity.of(snapshotService.latest(organizationId, vppId)
                .map(FlexibilitySnapshotResponse::from));
    }
}
