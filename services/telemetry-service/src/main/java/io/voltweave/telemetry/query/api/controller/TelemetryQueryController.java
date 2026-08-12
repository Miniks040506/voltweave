package io.voltweave.telemetry.query.api.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.voltweave.telemetry.access.PortfolioAccessClient;
import io.voltweave.telemetry.query.api.response.DeviceTwinResponse;
import io.voltweave.telemetry.query.api.response.TelemetryPointResponse;
import io.voltweave.telemetry.query.application.TelemetryQueryService;
import io.voltweave.telemetry.realtime.SiteTelemetryBroadcaster;

@RestController
public class TelemetryQueryController {
    private final PortfolioAccessClient accessClient;
    private final TelemetryQueryService queryService;
    private final SiteTelemetryBroadcaster broadcaster;

    public TelemetryQueryController(
            PortfolioAccessClient accessClient,
            TelemetryQueryService queryService,
            SiteTelemetryBroadcaster broadcaster
    ) {
        this.accessClient = accessClient;
        this.queryService = queryService;
        this.broadcaster = broadcaster;
    }

    @GetMapping("/api/v1/sites/{siteId}/telemetry")
    public List<TelemetryPointResponse> history(
            @PathVariable UUID siteId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "500") int limit,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizationId = accessClient.requireSiteAccess(jwt.getSubject(), siteId);
        return queryService.siteHistory(organizationId, siteId, from, to, limit)
                .stream().map(TelemetryPointResponse::from).toList();
    }

    @GetMapping("/api/v1/sites/{siteId}/live")
    public List<DeviceTwinResponse> live(
            @PathVariable UUID siteId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizationId = accessClient.requireSiteAccess(jwt.getSubject(), siteId);
        return queryService.siteTwins(organizationId, siteId)
                .stream().map(DeviceTwinResponse::from).toList();
    }

    @GetMapping("/api/v1/devices/{deviceId}/twin")
    public ResponseEntity<DeviceTwinResponse> twin(
            @PathVariable UUID deviceId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID organizationId = accessClient.requireDeviceAccess(jwt.getSubject(), deviceId);
        return ResponseEntity.of(queryService.deviceTwin(organizationId, deviceId)
                .map(DeviceTwinResponse::from));
    }

    @GetMapping(
            value = "/api/v1/stream/sites/{siteId}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter stream(
            @PathVariable UUID siteId,
            @AuthenticationPrincipal Jwt jwt
    ) throws IOException {
        UUID organizationId = accessClient.requireSiteAccess(jwt.getSubject(), siteId);
        var emitter = broadcaster.subscribe(organizationId, siteId);
        var snapshot = queryService.siteTwins(organizationId, siteId)
                .stream().map(DeviceTwinResponse::from).toList();
        emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        return emitter;
    }
}
