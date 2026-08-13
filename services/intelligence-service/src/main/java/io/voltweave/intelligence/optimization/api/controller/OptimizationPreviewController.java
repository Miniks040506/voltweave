package io.voltweave.intelligence.optimization.api.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.intelligence.access.PortfolioAccessClient;
import io.voltweave.intelligence.optimization.api.request.GenerateOptimizationPreviewRequest;
import io.voltweave.intelligence.optimization.api.response.OptimizationPreviewResponse;
import io.voltweave.intelligence.optimization.application.OptimizationApplicationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vpps/{vppId}/optimization-preview")
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class OptimizationPreviewController {
    private final PortfolioAccessClient accessClient;
    private final OptimizationApplicationService optimizationService;

    public OptimizationPreviewController(
            PortfolioAccessClient accessClient,
            OptimizationApplicationService optimizationService
    ) {
        this.accessClient = accessClient;
        this.optimizationService = optimizationService;
    }

    @PostMapping
    public ResponseEntity<OptimizationPreviewResponse> generate(
            @PathVariable UUID vppId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateOptimizationPreviewRequest request
    ) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), vppId);
        var preview = optimizationService.generate(
                organizationId, vppId,
                request.targetPowerKw(), request.reserveMarginPercent()
        );
        return ResponseEntity.created(URI.create(
                "/api/v1/vpps/" + vppId + "/optimization-preview/" + preview.id()
        )).body(OptimizationPreviewResponse.from(preview));
    }
}
