package com.campusguinness.interfaces.web.activityapplication;

import com.campusguinness.activity.application.query.port.ActivityApplicationQueryPort;
import com.campusguinness.activity.application.result.ActivityApplicationResult;
import com.campusguinness.activity.application.service.ActivityApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/activity-applications")
public class AdminActivityApplicationController {

    private final ActivityApplicationService service;
    private final ActivityApplicationQueryPort queryPort;
    private final CurrentActor currentActor;

    public AdminActivityApplicationController(ActivityApplicationService service,
                                               ActivityApplicationQueryPort queryPort,
                                               CurrentActor currentActor) {
        this.service = service;
        this.queryPort = queryPort;
        this.currentActor = currentActor;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PageResponse<ActivityApplicationListItem>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID schoolId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        var result = queryPort.findAll(status, schoolId, page, size);
        var items = result.items().stream()
                .map(r -> new ActivityApplicationListItem(r.applicationId(), r.schoolId(), r.title(),
                        r.status(), r.createdActivityId(), r.reviewedAt(),
                        r.reviewComment(), r.rejectReason(), r.applicationVersion()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ActivityApplicationResponse> get(@PathVariable UUID id) {
        return queryPort.findById(id)
                .map(r -> ResponseEntity.ok(ActivityApplicationResponse.from(r)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ActivityApplicationResponse> approve(@PathVariable UUID id) {
        ActivityApplicationResult r = service.approve(id, currentActor.requireUserId());
        return ResponseEntity.ok(ActivityApplicationResponse.from(r));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ActivityApplicationResponse> reject(@PathVariable UUID id,
                                                               @Valid @RequestBody RejectActivityApplicationRequest req) {
        ActivityApplicationResult r = service.reject(id, currentActor.requireUserId(), req.reason());
        return ResponseEntity.ok(ActivityApplicationResponse.from(r));
    }
}
