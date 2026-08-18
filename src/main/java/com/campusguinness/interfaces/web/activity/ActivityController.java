package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.campusguinness.interfaces.web.common.PageResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private final ActivityManagementService service;
    private final ActivityQueryService queryService;

    public ActivityController(ActivityManagementService service, ActivityQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ActivityListItem>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listPublic(page, size);
        var items = result.items().stream()
                .map(r -> new ActivityListItem(r.id(), r.schoolId(), r.schoolName(), r.schoolRegion(),
                        r.title(), r.startTime(), r.endTime(), r.location(), r.executionStatus()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ActivityDetailResponse> detail(@PathVariable UUID id) {
        var result = queryService.publicDetail(id);
        return ResponseEntity.ok(ActivityDetailResponse.from(result));
    }

    @GetMapping("/management")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<PageResponse<ActivityManagementListItem>> managementList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) UUID projectId) {
        var result = queryService.listManagement(page, size, status, query, projectId);
        var items = result.items().stream().map(r -> new ActivityManagementListItem(
                r.id(), r.title(), r.projectName(), r.ruleVersionNumber(), r.executionStatus(),
                r.publicStatus(), r.startTime(), r.endTime(), r.updatedAt())).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/management/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityManagementDetailResponse> managementDetail(@PathVariable UUID id) {
        var r = queryService.managementDetail(id);
        return ResponseEntity.ok(new ActivityManagementDetailResponse(r.id(), r.schoolId(), r.schoolName(),
                r.title(), r.description(), r.startTime(), r.endTime(), r.location(), r.executionStatus(),
                r.publicStatus(), r.createdAt(), r.updatedAt(), r.projects()));
    }

    @PostMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResponse> create(@Valid @RequestBody CreateActivityRequest req) {
        var cmd = new CreateActivityCommand(req.projectId(), req.title(),
                req.description(), req.startTime(), req.endTime(), req.location());
        ActivityResult r = service.create(cmd);
        return ResponseEntity.created(URI.create("/api/v1/activities/" + r.id()))
                .body(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResponse> update(@PathVariable UUID id,
            @Valid @RequestBody UpdateActivityRequest req) {
        var r = service.update(id, new com.campusguinness.activity.application.command.UpdateActivityCommand(
                req.title(), req.description(), req.startTime(), req.endTime(), req.location()));
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResponse> publish(@PathVariable UUID id) {
        ActivityResult r = service.publish(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<ActivityResponse> cancel(@PathVariable UUID id) {
        ActivityResult r = service.cancel(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }
}
