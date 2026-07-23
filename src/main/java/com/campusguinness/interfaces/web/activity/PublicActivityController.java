package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.ActivityQueryService;
import com.campusguinness.activity.application.service.ActivityManagementService;
import com.campusguinness.interfaces.web.common.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
public class PublicActivityController {
    private final ActivityManagementService service;
    private final ActivityQueryService queryService;

    public PublicActivityController(ActivityManagementService service, ActivityQueryService queryService) {
        this.service = service;
        this.queryService = queryService;
    }

    @GetMapping("/activities")
    public ResponseEntity<PageResponse<PublicActivityItem>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = queryService.listPublic(page, size);
        var items = result.items().stream()
                .map(r -> new PublicActivityItem(r.id(), r.schoolId(), r.title(),
                        r.startTime(), r.endTime(), r.location(), r.executionStatus()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/activities/{activityId}")
    public ResponseEntity<PublicActivityDetail> getDetail(@PathVariable UUID activityId) {
        try {
            Map<String, Object> a = service.getPublicDetail(activityId);
            var projects = service.getPublicProjects(activityId).stream()
                    .map(p -> new PublicProject(p.id(), p.projectId())).toList();
            return ResponseEntity.ok(new PublicActivityDetail(
                    (UUID)a.get("id"), (String)a.get("title"), (String)a.get("description"),
                    (String)a.get("execution_status"), projects));
        } catch (IllegalArgumentException e) { return ResponseEntity.notFound().build(); }
    }

    public record PublicActivityItem(UUID id, UUID schoolId, String title,
            java.time.Instant startTime, java.time.Instant endTime, String location, String status) {}
    public record PublicActivityDetail(UUID id, String title, String description, String status, List<PublicProject> projects) {}
    public record PublicProject(UUID id, UUID projectId) {}
}
