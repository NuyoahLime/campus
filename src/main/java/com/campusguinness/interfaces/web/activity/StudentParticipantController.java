package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.port.StudentActivityQueryPort;
import com.campusguinness.activity.application.query.port.StudentProjectQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;
import com.campusguinness.project.application.query.model.QueryPage;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentParticipantController {

    private final StudentActivityQueryPort activityQueryPort;
    private final StudentProjectQueryPort projectQueryPort;
    private final CurrentActor currentActor;

    public StudentParticipantController(StudentActivityQueryPort activityQueryPort,
                                         StudentProjectQueryPort projectQueryPort,
                                         CurrentActor currentActor) {
        this.activityQueryPort = activityQueryPort;
        this.projectQueryPort = projectQueryPort;
        this.currentActor = currentActor;
    }

    private UUID studentId() { return currentActor.requireUserId(); }

    // ── Activities ──

    @GetMapping("/activities/mine")
    public ResponseEntity<PageResponse<StudentActivityItem>> listMyActivities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");

        var result = activityQueryPort.findMine(studentId(), page, size);
        var items = result.items().stream().map(a -> new StudentActivityItem(
                a.activityId(), a.title(), a.descriptionSummary(),
                a.startTime(), a.endTime(), a.location(),
                a.executionStatus(), a.assignedProjectCount())).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/activities/mine/{activityId}")
    public ResponseEntity<StudentActivityDetail> getMyActivity(@PathVariable UUID activityId) {
        return activityQueryPort.findMineById(studentId(), activityId)
                .map(a -> ResponseEntity.ok(new StudentActivityDetail(
                        a.activityId(), a.title(), a.description(),
                        a.startTime(), a.endTime(), a.location(),
                        a.executionStatus(),
                        a.projects().stream().map(p -> new AssignedProjectItem(
                                p.activityProjectId(), p.projectId(), p.projectName(),
                                p.category(), p.scoreStorageType(), p.scoreUnit(),
                                p.latestAttemptId(), p.latestAttemptStatus(),
                                p.latestScoreDisplay(), p.hasApprovedScore())).toList())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Projects ──

    @GetMapping("/activity-projects/mine")
    public ResponseEntity<PageResponse<StudentProjectItem>> listMyProjects(
            @RequestParam(required = false) String executionStatus,
            @RequestParam(required = false) String scoreStatus,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");

        var result = projectQueryPort.findMine(studentId(), executionStatus, scoreStatus, keyword, page, size);
        var items = result.items().stream().map(p -> new StudentProjectItem(
                p.activityProjectId(), p.activityId(), p.activityTitle(),
                p.projectId(), p.projectName(), p.category(),
                p.scoreStorageType(), p.comparisonDirection(), p.scoreUnit(),
                p.attemptCount(), p.latestAttemptId(), p.latestAttemptStatus(),
                p.latestScoreDisplay(), p.hasApprovedScore(), p.assignedAt())).toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @GetMapping("/activity-projects/mine/{activityProjectId}")
    public ResponseEntity<StudentProjectDetail> getMyProject(@PathVariable UUID activityProjectId) {
        return projectQueryPort.findMineById(studentId(), activityProjectId)
                .map(p -> ResponseEntity.ok(new StudentProjectDetail(
                        p.activityProjectId(), p.activityId(), p.activityTitle(),
                        p.projectId(), p.projectName(), p.category(),
                        p.scoreStorageType(), p.comparisonDirection(), p.scoreUnit(),
                        p.attemptCount(), p.latestAttemptId(), p.latestAttemptStatus(),
                        p.latestScoreDisplay(), p.hasApprovedScore(), p.assignedAt(),
                        p.activityDescription(), p.activityStartTime(), p.activityEndTime(),
                        p.location(), p.projectDescription(), p.rulesText(),
                        p.venueRequirements(), p.equipmentRequirements(),
                        p.effectiveScoreRule(), p.allowTie(), p.decimalPlaces(), p.gradeOrder())))
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs (match front-end types exactly) ──

    public record StudentActivityItem(UUID activityId, String title, String descriptionSummary,
            java.time.Instant startTime, java.time.Instant endTime, String location,
            String executionStatus, int assignedProjectCount) {}

    public record StudentActivityDetail(UUID activityId, String title, String description,
            java.time.Instant startTime, java.time.Instant endTime, String location,
            String executionStatus, java.util.List<AssignedProjectItem> projects) {}

    public record AssignedProjectItem(UUID activityProjectId, UUID projectId, String projectName,
            String category, String scoreStorageType, String scoreUnit,
            UUID latestAttemptId, String latestAttemptStatus, String latestScoreDisplay,
            boolean hasApprovedScore) {}

    public record StudentProjectItem(UUID activityProjectId, UUID activityId, String activityTitle,
            UUID projectId, String projectName, String category,
            String scoreStorageType, String comparisonDirection, String scoreUnit,
            int attemptCount, UUID latestAttemptId, String latestAttemptStatus,
            String latestScoreDisplay, boolean hasApprovedScore, java.time.Instant assignedAt) {}

    public record StudentProjectDetail(UUID activityProjectId, UUID activityId, String activityTitle,
            UUID projectId, String projectName, String category,
            String scoreStorageType, String comparisonDirection, String scoreUnit,
            int attemptCount, UUID latestAttemptId, String latestAttemptStatus,
            String latestScoreDisplay, boolean hasApprovedScore, java.time.Instant assignedAt,
            String activityDescription, java.time.Instant activityStartTime,
            java.time.Instant activityEndTime, String location,
            String projectDescription, String rulesText, String venueRequirements,
            String equipmentRequirements, String effectiveScoreRule,
            Boolean allowTie, Integer decimalPlaces, String gradeOrder) {}
}
