package com.campusguinness.interfaces.web.activityresult;

import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.result.application.query.ActivityResultReadQueryService;
import com.campusguinness.result.application.query.model.ActivityResultHistoryEntry;
import com.campusguinness.result.application.query.model.ManagementActivityResultSummary;
import com.campusguinness.result.application.query.model.PublicActivityResultView;
import com.campusguinness.result.application.query.model.StudentActivityResultView;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ActivityResultReadController {
    private final ActivityResultReadQueryService service;

    public ActivityResultReadController(ActivityResultReadQueryService service) {
        this.service = service;
    }

    @GetMapping("/public/activities/{activityId}/result")
    public ResponseEntity<PublicActivityResultView> publicDetail(@PathVariable UUID activityId) {
        return ResponseEntity.ok(service.publicDetail(activityId));
    }

    @GetMapping("/student/activities/{activityId}/result")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<StudentActivityResultView> studentDetail(@PathVariable UUID activityId) {
        return ResponseEntity.ok(service.studentDetail(activityId));
    }

    @GetMapping("/school-admin/activity-results")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<PageResponse<ManagementActivityResultSummary>> managementList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String internalStatus,
            @RequestParam(required = false) String publicStatus,
            @RequestParam(name = "q", required = false) String query) {
        var result = service.managementList(page, size, internalStatus, publicStatus, query);
        return ResponseEntity.ok(PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/school-admin/activities/{activityId}/result/history")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<List<ActivityResultHistoryEntry>> history(@PathVariable UUID activityId) {
        return ResponseEntity.ok(service.history(activityId));
    }
}
