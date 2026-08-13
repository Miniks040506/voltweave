package io.voltweave.portfolio.device.api.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.device.api.response.FlexibilityResourceResponse;
import io.voltweave.portfolio.device.application.FlexibilityResourceQueryService;

@RestController
@RequestMapping("/internal/v1/vpps/{vppId}/flexibility-resources")
public class InternalFlexibilityResourceController {
    private final FlexibilityResourceQueryService queryService;

    public InternalFlexibilityResourceController(
            FlexibilityResourceQueryService queryService
    ) {
        this.queryService = queryService;
    }

    @GetMapping
    public List<FlexibilityResourceResponse> list(@PathVariable UUID vppId) {
        return queryService.listForVpp(vppId).stream()
                .map(FlexibilityResourceResponse::from)
                .toList();
    }
}
