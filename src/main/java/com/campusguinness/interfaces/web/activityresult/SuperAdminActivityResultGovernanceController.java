package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.result.application.query.ActivityResultGovernanceQueryService;
import com.campusguinness.result.application.query.model.GovernanceActivityResultDetail;
import com.campusguinness.result.application.query.model.GovernanceActivityResultSummary;
import com.campusguinness.result.application.service.ActivityResultGovernanceApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin/activity-results")
public class SuperAdminActivityResultGovernanceController {
    private final ActivityResultGovernanceApplicationService service;
    private final ActivityResultGovernanceQueryService queryService;

    public SuperAdminActivityResultGovernanceController(
            ActivityResultGovernanceApplicationService service,
            ActivityResultGovernanceQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/governance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<GovernanceActivityResultSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String publicStatus,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false, name = "q") String query) {
        var result = queryService.list(page, size, publicStatus, blocked, query);
        return ResponseEntity.ok(PageResponse.of(result.items(), result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}/governance")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<GovernanceActivityResultDetail> detail(
            @PathVariable UUID id) {
        return ResponseEntity.ok(queryService.detail(id));
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

    @PostMapping("/{id}/reset-takedown")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ActivityResultResponse> resetTakedown(
            @PathVariable UUID id,
            @Valid @RequestBody ResetTakedownActivityResultRequest request) {
        var result = service.resetTakedown(id, request.reason());
        return ResponseEntity.ok(new ActivityResultResponse(
                result.id(), result.internalStatus(), result.publicStatus()));
    }
}
