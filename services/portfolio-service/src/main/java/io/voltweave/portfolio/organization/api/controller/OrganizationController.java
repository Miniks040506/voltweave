package io.voltweave.portfolio.organization.api.controller;

import java.net.URI;
import java.util.List;
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

import io.voltweave.portfolio.organization.api.request.AddOrganizationMemberRequest;
import io.voltweave.portfolio.organization.api.request.CreateOrganizationRequest;
import io.voltweave.portfolio.organization.api.response.OrganizationResponse;
import io.voltweave.portfolio.organization.application.OrganizationService;
import io.voltweave.portfolio.organization.application.command.CreateOrganizationCommand;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @GetMapping
    public List<OrganizationResponse> findAll(@AuthenticationPrincipal Jwt jwt) {
        return organizationService.findAllForSubject(jwt.getSubject()).stream()
                .map(OrganizationResponse::from).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<OrganizationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrganizationRequest request
    ) {
        var organization = organizationService.create(
                new CreateOrganizationCommand(
                        request.type(), request.legalName(), request.displayName(),
                        request.tenantCode(), request.country(), request.timezone()
                ),
                jwt.getSubject()
        );
        return ResponseEntity.created(URI.create("/api/v1/organizations/" + organization.id()))
                .body(OrganizationResponse.from(organization));
    }

    @GetMapping("/{organizationId}")
    public OrganizationResponse findById(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId
    ) {
        return OrganizationResponse.from(
                organizationService.findForSubject(organizationId, jwt.getSubject())
        );
    }

    @PostMapping("/{organizationId}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddOrganizationMemberRequest request
    ) {
        var member = organizationService.addMember(
                organizationId, jwt.getSubject(), request.subjectId(), request.role()
        );
        return ResponseEntity.created(URI.create(
                "/api/v1/organizations/" + organizationId + "/members/" + member.id()
        )).build();
    }
}
