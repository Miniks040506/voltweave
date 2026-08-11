package io.voltweave.portfolio.site.api.controller;

import java.net.URI;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.site.api.request.CreateSiteRequest;
import io.voltweave.portfolio.site.api.request.UpdateSitePreferenceRequest;
import io.voltweave.portfolio.site.api.request.UpdateSiteRequest;
import io.voltweave.portfolio.site.api.response.SiteResponse;
import io.voltweave.portfolio.site.application.CreateSiteCommand;
import io.voltweave.portfolio.site.application.SiteApplicationService;
import io.voltweave.portfolio.site.application.UpdateSiteCommand;
import io.voltweave.portfolio.site.application.UpdateSitePreferenceCommand;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/sites")
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class SiteController {
    private final SiteApplicationService siteService;

    public SiteController(SiteApplicationService siteService) {
        this.siteService = siteService;
    }

    @PostMapping
    public ResponseEntity<SiteResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSiteRequest request
    ) {
        var profile = siteService.create(
                new CreateSiteCommand(
                        request.organizationId(), request.name(), request.timezone(),
                        request.region(), request.country()
                ),
                jwt.getSubject()
        );
        return ResponseEntity.created(URI.create("/api/v1/sites/" + profile.site().id()))
                .body(SiteResponse.from(profile));
    }

    @GetMapping("/{siteId}")
    public SiteResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID siteId
    ) {
        return SiteResponse.from(siteService.findForSubject(siteId, jwt.getSubject()));
    }

    @PutMapping("/{siteId}")
    public SiteResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID siteId,
            @Valid @RequestBody UpdateSiteRequest request
    ) {
        return SiteResponse.from(siteService.update(
                siteId,
                new UpdateSiteCommand(
                        request.name(), request.timezone(), request.region(), request.country()
                ),
                jwt.getSubject()
        ));
    }

    @PatchMapping("/{siteId}/preferences")
    public SiteResponse updatePreference(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID siteId,
            @Valid @RequestBody UpdateSitePreferenceRequest request
    ) {
        return SiteResponse.from(siteService.updatePreference(
                siteId,
                new UpdateSitePreferenceCommand(
                        request.vppOptIn(), request.minimumBatteryReservePercent()
                ),
                jwt.getSubject()
        ));
    }
}
