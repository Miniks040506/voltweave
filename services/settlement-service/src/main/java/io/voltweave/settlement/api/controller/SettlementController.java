package io.voltweave.settlement.api.controller;

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.settlement.access.PortfolioAccessClient;
import io.voltweave.settlement.api.request.CreateRewardAdjustmentRequest;
import io.voltweave.settlement.api.response.RewardAdjustmentResponse;
import io.voltweave.settlement.api.response.SettlementResponse;
import io.voltweave.settlement.application.RewardApplicationService;
import io.voltweave.settlement.application.SettlementApplicationService;
import io.voltweave.settlement.application.SettlementCsvReportService;
import io.voltweave.settlement.application.model.Settlement;
import jakarta.validation.Valid;

@RestController
@PreAuthorize("hasAnyRole('VPP_OPERATOR', 'ADMIN')")
public class SettlementController {
    private final SettlementApplicationService service;
    private final RewardApplicationService rewardService;
    private final SettlementCsvReportService csvReportService;
    private final PortfolioAccessClient accessClient;

    public SettlementController(
            SettlementApplicationService service,
            RewardApplicationService rewardService,
            SettlementCsvReportService csvReportService,
            PortfolioAccessClient accessClient
    ) {
        this.service = service;
        this.rewardService = rewardService;
        this.csvReportService = csvReportService;
        this.accessClient = accessClient;
    }

    @GetMapping("/api/v1/reports/dispatches/{dispatchId}.csv")
    public ResponseEntity<String> csv(
            @PathVariable UUID dispatchId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Settlement settlement = service.findByDispatch(dispatchId).orElse(null);
        if (settlement == null) {
            return ResponseEntity.notFound().build();
        }
        requireAccess(jwt, settlement);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=dispatch-" + dispatchId + "-settlement.csv")
                .body(csvReportService.create(settlement));
    }

    @PostMapping("/api/v1/settlements/{settlementId}/adjustments")
    public ResponseEntity<RewardAdjustmentResponse> adjust(
            @PathVariable UUID settlementId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRewardAdjustmentRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        Settlement settlement = service.find(settlementId).orElse(null);
        if (settlement == null) {
            return ResponseEntity.notFound().build();
        }
        requireAccess(jwt, settlement);
        return ResponseEntity.ok(RewardAdjustmentResponse.from(rewardService.adjust(
                settlement, request.siteId(), request.amount(), request.reason(),
                jwt.getSubject(), idempotencyKey
        )));
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
            requireAccess(jwt, settlement);
            return SettlementResponse.from(settlement);
        }));
    }

    private void requireAccess(Jwt jwt, Settlement settlement) {
        UUID organizationId = accessClient.requireVppAccess(
                jwt.getSubject(), settlement.vppId()
        );
        if (!organizationId.equals(settlement.organizationId())) {
            throw new AccessDeniedException("Settlement access denied");
        }
    }
}
