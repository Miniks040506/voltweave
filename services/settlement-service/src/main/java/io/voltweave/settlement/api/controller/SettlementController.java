package io.voltweave.settlement.api.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.settlement.access.PortfolioAccessClient;
import io.voltweave.settlement.api.response.SettlementResponse;
import io.voltweave.settlement.application.SettlementApplicationService;
import io.voltweave.settlement.application.model.Settlement;

@RestController
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class SettlementController {
    private final SettlementApplicationService service;
    private final PortfolioAccessClient accessClient;

    public SettlementController(
            SettlementApplicationService service,
            PortfolioAccessClient accessClient
    ) {
        this.service = service;
        this.accessClient = accessClient;
    }

    @GetMapping("/api/v1/settlements/{settlementId}")
    public ResponseEntity<SettlementResponse> get(
            @PathVariable UUID settlementId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return response(service.find(settlementId), jwt);
    }

    @GetMapping("/api/v1/dispatches/{dispatchId}/settlements")
    public ResponseEntity<SettlementResponse> getByDispatch(
            @PathVariable UUID dispatchId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return response(service.findByDispatch(dispatchId), jwt);
    }

    private ResponseEntity<SettlementResponse> response(
            Optional<Settlement> result,
            Jwt jwt
    ) {
        return ResponseEntity.of(result.map(settlement -> {
            UUID organizationId = accessClient.requireVppAccess(
                    jwt.getSubject(), settlement.vppId()
            );
            if (!organizationId.equals(settlement.organizationId())) {
                throw new AccessDeniedException("Settlement access denied");
            }
            return SettlementResponse.from(settlement);
        }));
    }
}
