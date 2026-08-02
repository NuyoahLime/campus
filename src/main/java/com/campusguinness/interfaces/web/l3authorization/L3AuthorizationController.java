package com.campusguinness.interfaces.web.l3authorization;

import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.ranking.application.result.L3AuthorizationResult;
import com.campusguinness.ranking.application.service.L3AuthorizationApplicationService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/l3-authorizations")
public class L3AuthorizationController {
    private final L3AuthorizationApplicationService service;
    private final CurrentActor currentActor;
    public L3AuthorizationController(L3AuthorizationApplicationService s, CurrentActor currentActor) {
        this.service = s; this.currentActor = currentActor;
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> submit(@Valid @RequestBody CreateL3AuthorizationRequest req) {
        var r = service.submit(req.schoolId(), req.projectId(), req.ruleVersionId());
        return ResponseEntity.created(URI.create("/api/v1/l3-authorizations/" + r.id()))
                .body(new L3AuthorizationResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> approve(@PathVariable UUID id, @Valid @RequestBody ApproveL3AuthorizationRequest req) {
        var r = service.approve(id, currentActor.requireUserId(), req.comment());
        return ResponseEntity.ok(new L3AuthorizationResponse(r.id(), r.status()));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<L3AuthorizationResponse> withdraw(@PathVariable UUID id, @Valid @RequestBody WithdrawL3AuthorizationRequest req) {
        var r = service.withdraw(id, req.reason());
        return ResponseEntity.ok(new L3AuthorizationResponse(r.id(), r.status()));
    }
}
