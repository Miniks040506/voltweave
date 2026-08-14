package io.voltweave.portfolio.access.api.controller;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.voltweave.portfolio.access.api.request.AccessCheckRequest;
import io.voltweave.portfolio.access.api.request.SubjectSitesRequest;
import io.voltweave.portfolio.access.api.response.AccessCheckResponse;
import io.voltweave.portfolio.access.api.response.SubjectSitesResponse;
import io.voltweave.portfolio.access.application.AccessCheckService;

@RestController
@RequestMapping("/internal/v1/access-checks")
public class InternalAccessController {
    private final AccessCheckService accessCheckService;

    public InternalAccessController(AccessCheckService accessCheckService) {
        this.accessCheckService = accessCheckService;
    }

    @PostMapping
    public ResponseEntity<AccessCheckResponse> check(
            @Valid @RequestBody AccessCheckRequest request
    ) {
        return ResponseEntity.ok(AccessCheckResponse.from(
                accessCheckService.check(request.toCommand())
        ));
    }

    @PostMapping("/sites")
    public ResponseEntity<SubjectSitesResponse> sites(
            @Valid @RequestBody SubjectSitesRequest request
    ) {
        return ResponseEntity.ok(new SubjectSitesResponse(
                accessCheckService.siteIdsForSubject(request.subjectId())
        ));
    }
}
