package io.voltweave.intelligence.optimization.api.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.intelligence.optimization.application.OptimizationApplicationService;
import io.voltweave.intelligence.optimization.application.model.DispatchInput;

@RestController
@RequestMapping("/internal/v1/vpps/{vppId}/dispatch-inputs/{previewId}")
public class InternalDispatchInputController {
    private final OptimizationApplicationService optimizationService;

    public InternalDispatchInputController(OptimizationApplicationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @GetMapping
    public DispatchInput get(
            @PathVariable UUID vppId,
            @PathVariable UUID previewId,
            @RequestParam UUID organizationId
    ) {
        return optimizationService.dispatchInput(organizationId, vppId, previewId);
    }
}
