package io.voltweave.portfolio.audit.api.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.audit.api.response.AuditEntryResponse;
import io.voltweave.portfolio.audit.application.AuditService;

@Validated
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public List<AuditEntryResponse> findRecent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID organizationId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit
    ) {
        return auditService.findForSubject(
                organizationId, jwt.getSubject(), limit
        ).stream().map(AuditEntryResponse::from).toList();
    }
}
