package io.voltweave.portfolio.vpp.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.vpp.api.response.AutomationPolicyResponse;
import io.voltweave.portfolio.vpp.application.VppApplicationService;

@RestController
@RequestMapping("/internal/v1/vpps/{vppId}/automation-policy")
public class InternalAutomationPolicyController {
    private final VppApplicationService vppService;

    public InternalAutomationPolicyController(VppApplicationService vppService) {
        this.vppService = vppService;
    }

    @GetMapping
    public AutomationPolicyResponse get(
            @PathVariable UUID vppId,
            @RequestParam UUID organizationId
    ) {
        return AutomationPolicyResponse.from(vppService.currentPolicy(organizationId, vppId));
    }
}

