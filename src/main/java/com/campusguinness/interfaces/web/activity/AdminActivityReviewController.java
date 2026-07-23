package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/activities")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminActivityReviewController {

    private final ActivityManagementService service;
    private final ActivityQueryService queryService;
    private final CurrentActor currentActor;

    public AdminActivityReviewController(ActivityManagementService service,
                                          ActivityQueryService queryService,
                                          CurrentActor currentActor) {
        this.service = service;
        this.queryService = queryService;
        this.currentActor = currentActor;
    }

    // ── Public Review Queue ──

    @GetMapping("/public-review")
    public ResponseEntity<PageResponse<ActivityListItem>> listReview(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String publicStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listPublicReview(schoolId, publicStatus, page, size);
        var items = result.items().stream()
                .map(r -> new ActivityListItem(r.id(), r.schoolId(), r.title(),
                        r.startTime(), r.endTime(), r.location(), r.executionStatus(), null))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/public-review/{activityId}")
    public ResponseEntity<ActivityDetail> getReviewDetail(@PathVariable UUID activityId) {
        var act = service.findById(activityId);
        var projects = service.listProjects(activityId).stream()
                .map(p -> new ActivityProjectResponse(p.id(), p.activityId(), p.projectId()))
                .toList();
        return ResponseEntity.ok(new ActivityDetail(
                act.id().value(), act.schoolId(), act.title(), act.description(),
                act.startTime(), act.endTime(), act.location(),
                act.executionStatus().name(), act.publicStatus().name(), act.createdBy(),
                projects));
    }

    // ── Review Actions ──

    @PostMapping("/{id}/approve-public-review")
    public ResponseEntity<ActivityResponse> approveReview(@PathVariable UUID id) {
        ActivityResult r = service.platformApprove(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/reject-public-review")
    public ResponseEntity<ActivityResponse> rejectReview(@PathVariable UUID id,
                                                          @Valid @RequestBody RejectReviewRequest req) {
        ActivityResult r = service.platformReject(id, req.reason());
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/make-public")
    public ResponseEntity<ActivityResponse> makePublic(@PathVariable UUID id) {
        ActivityResult r = service.makePublic(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/take-down")
    public ResponseEntity<ActivityResponse> takeDown(@PathVariable UUID id,
                                                      @Valid @RequestBody TakedownRequest req) {
        ActivityResult r = service.platformTakedown(id, req.reason());
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    // ── DTOs ──

    public record RejectReviewRequest(@NotBlank String reason) {}
    public record TakedownRequest(@NotBlank String reason) {}
    public record ActivityDetail(UUID activityId, UUID schoolId, String title,
            String description, java.time.Instant startTime, java.time.Instant endTime,
            String location, String executionStatus, String publicStatus, UUID createdBy,
            List<ActivityProjectResponse> projects) {}
}
