package io.voltweave.settlement.api.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.settlement.access.PortfolioAccessClient;
import io.voltweave.settlement.api.response.EarningsResponse;
import io.voltweave.settlement.application.RewardApplicationService;

@RestController
@PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
public class CustomerEarningsController {
    private final RewardApplicationService service;
    private final PortfolioAccessClient accessClient;

    public CustomerEarningsController(
            RewardApplicationService service,
            PortfolioAccessClient accessClient
    ) {
        this.service = service;
        this.accessClient = accessClient;
    }

    @GetMapping("/api/v1/customers/me/earnings")
    public EarningsResponse get(@AuthenticationPrincipal Jwt jwt) {
        return EarningsResponse.from(service.earningsForSites(
                accessClient.siteIdsForSubject(jwt.getSubject())
        ));
    }
}
