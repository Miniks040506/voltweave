package io.voltweave.intelligence.forecast.api.controller;

import java.net.URI;
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
import io.voltweave.intelligence.forecast.api.request.GenerateForecastRequest;
import io.voltweave.intelligence.forecast.api.response.ForecastResponse;
import io.voltweave.intelligence.forecast.application.ForecastApplicationService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/vpps/{vppId}/forecast")
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class ForecastController {
    private final PortfolioAccessClient accessClient;
    private final ForecastApplicationService forecastService;

    public ForecastController(
            PortfolioAccessClient accessClient,
            ForecastApplicationService forecastService
    ) {
        this.accessClient = accessClient;
        this.forecastService = forecastService;
    }

    @PostMapping
    public ResponseEntity<ForecastResponse> generate(
            @PathVariable UUID vppId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody GenerateForecastRequest request
    ) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), vppId);
        var forecast = forecastService.generate(
                organizationId, vppId, request.horizon(), request.targetStart()
        );
        return ResponseEntity.created(URI.create(
                "/api/v1/vpps/" + vppId + "/forecast"
        )).body(ForecastResponse.from(forecast));
    }

    @GetMapping
    public ResponseEntity<ForecastResponse> latest(
            @PathVariable UUID vppId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizationId = accessClient.requireVppAccess(jwt.getSubject(), vppId);
        return ResponseEntity.of(forecastService.latest(organizationId, vppId)
                .map(ForecastResponse::from));
    }
}
