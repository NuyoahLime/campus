package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.command.CreateActivityCommand;
import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.result.ActivityResult;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.campusguinness.interfaces.web.common.PageResponse;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
                .map(r -> new ActivityListItem(r.id(), r.schoolId(), r.title(),
                        r.startTime(), r.endTime(), r.location(), r.executionStatus()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @PostMapping
    public ResponseEntity<ActivityResponse> create(@Valid @RequestBody CreateActivityRequest req) {
        var cmd = new CreateActivityCommand(req.schoolId(), req.title(),
                req.description(), req.startTime(), req.endTime(), req.location());
        ActivityResult r = service.create(cmd);
        return ResponseEntity.created(URI.create("/api/v1/activities/" + r.id()))
                .body(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ActivityResponse> publish(@PathVariable UUID id) {
        ActivityResult r = service.publish(id);
        return ResponseEntity.ok(new ActivityResponse(r.id(), r.executionStatus(), r.publicStatus()));
    }
}
