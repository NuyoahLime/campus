package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.result.application.service.ActivityResultGovernanceApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin/activity-results")
public class SuperAdminActivityResultGovernanceController {
    private final ActivityResultGovernanceApplicationService service;

    public SuperAdminActivityResultGovernanceController(
            ActivityResultGovernanceApplicationService service) {
        this.service = service;
    }

    @PostMapping("/{id}/takedown")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ActivityResultResponse> takedown(
            @PathVariable UUID id,
            @Valid @RequestBody TakedownActivityResultRequest request) {
        var result = service.takedown(id, request.reason());
        return ResponseEntity.ok(new ActivityResultResponse(
                result.id(), result.internalStatus(), result.publicStatus()));
    }
}
