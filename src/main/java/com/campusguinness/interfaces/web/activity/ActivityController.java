package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.interfaces.web.common.PageResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Public read-only endpoints for activities.
 * Write operations require school-admin scoped endpoints under
 * {@code /api/v1/school-admin/activities}.
 */
@RestController
@RequestMapping("/api/v1/activities")
public class ActivityController {

    private final ActivityQueryService queryService;

    public ActivityController(ActivityQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ActivityListItem>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listPublic(page, size);
        var items = result.items().stream()
                .map(r -> new ActivityListItem(r.id(), r.schoolId(), r.title(),
                        r.startTime(), r.endTime(), r.location(), r.executionStatus(), null))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }
}
