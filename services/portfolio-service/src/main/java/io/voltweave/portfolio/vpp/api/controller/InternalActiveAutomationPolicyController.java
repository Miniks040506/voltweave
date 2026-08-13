package io.voltweave.portfolio.vpp.api.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.vpp.api.response.ActiveAutomationPolicyResponse;
import io.voltweave.portfolio.vpp.application.VppApplicationService;

@RestController
@RequestMapping("/internal/v1/automation-policies")
public class InternalActiveAutomationPolicyController {
    private final VppApplicationService vppService;

    public InternalActiveAutomationPolicyController(VppApplicationService vppService) {
        this.vppService = vppService;
    }

    @GetMapping
    public List<ActiveAutomationPolicyResponse> active() {
        return vppService.activeAutomationPolicies().stream()
                .map(ActiveAutomationPolicyResponse::from).toList();
    }
}
