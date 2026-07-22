package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.service.ActivityManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public")
public class PublicActivityController {
    private final ActivityManagementService service;

    public PublicActivityController(ActivityManagementService service) { this.service = service; }

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

    public record PublicActivityDetail(UUID id, String title, String description, String status, List<PublicProject> projects) {}
    public record PublicProject(UUID id, UUID projectId) {}
}
