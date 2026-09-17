package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.result.application.query.ActivityResultReviewQueryService;
import com.campusguinness.result.application.query.model.PendingResultReviewDetail;
import com.campusguinness.result.application.query.model.PendingResultReviewSummary;
import com.campusguinness.result.application.service.ActivityResultReviewApplicationService;
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
public class SuperAdminActivityResultReviewController {
    private final ActivityResultReviewApplicationService service;
    private final ActivityResultReviewQueryService queryService;

    public SuperAdminActivityResultReviewController(
            ActivityResultReviewApplicationService service,
            ActivityResultReviewQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/public-reviews")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<PendingResultReviewSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listPending(page, size);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}/public-review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PendingResultReviewDetail> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(queryService.pendingDetail(id));
    }

    @PostMapping("/{id}/approve-public-review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ActivityResultResponse> approve(@PathVariable UUID id) {
        var result = service.approve(id);
        return ResponseEntity.ok(new ActivityResultResponse(
                result.id(), result.internalStatus(), result.publicStatus()));
    }

    @PostMapping("/{id}/reject-public-review")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ActivityResultResponse> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectActivityResultReviewRequest request) {
        var result = service.reject(id, request.reason());
        return ResponseEntity.ok(new ActivityResultResponse(
                result.id(), result.internalStatus(), result.publicStatus()));
    }
}
