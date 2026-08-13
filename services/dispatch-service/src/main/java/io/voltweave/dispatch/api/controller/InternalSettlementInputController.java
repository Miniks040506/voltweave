package io.voltweave.dispatch.api.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.dispatch.api.response.SettlementInputResponse;
import io.voltweave.dispatch.application.SettlementInputApplicationService;

@RestController
@RequestMapping("/internal/v1/dispatches/{dispatchId}/settlement-input")
public class InternalSettlementInputController {
    private final SettlementInputApplicationService service;

    public InternalSettlementInputController(SettlementInputApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SettlementInputResponse> get(@PathVariable UUID dispatchId) {
        return ResponseEntity.of(service.find(dispatchId).map(SettlementInputResponse::from));
    }
}
