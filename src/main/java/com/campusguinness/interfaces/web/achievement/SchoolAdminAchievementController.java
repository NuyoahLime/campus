package com.campusguinness.interfaces.web.achievement;

import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementDetail;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementItem;
import com.campusguinness.achievement.application.query.model.SchoolAdminAchievementStatus;
import com.campusguinness.achievement.application.service.SchoolAdminAchievementApplicationService;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/achievement-records")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminAchievementController {

    private final SchoolAdminAchievementApplicationService service;
    private final CurrentActor currentActor;

    public SchoolAdminAchievementController(
            SchoolAdminAchievementApplicationService service,
            CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @PutMapping("/ranking-entries/{rankingEntryId}")
    public ResponseEntity<SchoolAdminAchievementDetail> issue(
            @PathVariable UUID rankingEntryId) {
        var result = service.issue(
                currentActor.requireUserId(), rankingEntryId);
        SchoolAdminAchievementDetail body =
                result.record().withCreated(result.created());
        if (result.created()) {
            return ResponseEntity.created(URI.create(
                            "/api/v1/school-admin/achievement-records/"
                                    + body.recordId()))
                    .body(body);
        }
        return ResponseEntity.ok(body);
    }

    @GetMapping("/projects/{activityProjectId}")
    public PageResponse<SchoolAdminAchievementItem> listProjectRecords(
            @PathVariable UUID activityProjectId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = service.listProjectRecords(
                currentActor.requireUserId(),
                activityProjectId,
                status,
                keyword,
                page,
                size);
        return PageResponse.of(
                result.items(), result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/{recordId}")
    public SchoolAdminAchievementDetail get(
            @PathVariable UUID recordId) {
        return service.get(currentActor.requireUserId(), recordId);
    }

    @GetMapping("/ranking-versions/{rankingVersionId}/statuses")
    public List<SchoolAdminAchievementStatus> statuses(
            @PathVariable UUID rankingVersionId) {
        return service.getVersionStatuses(
                currentActor.requireUserId(), rankingVersionId);
    }
}
