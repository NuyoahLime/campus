package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.service.ActivityParticipantService;
import com.campusguinness.interfaces.web.common.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/activities")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAssignedActivityController {
    private final ActivityParticipantService service;

    public StudentAssignedActivityController(ActivityParticipantService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ActivityListItem> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        var result = service.listAssigned(page, size);
        var items = result.items().stream().map(r -> new ActivityListItem(
                r.id(), r.schoolId(), r.schoolName(), r.schoolRegion(), r.title(),
                r.startTime(), r.endTime(), r.location(), r.executionStatus())).toList();
        return PageResponse.of(items, result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/{id}")
    public ActivityDetailResponse detail(@PathVariable UUID id) {
        return ActivityDetailResponse.from(service.assignedDetail(id));
    }
}
