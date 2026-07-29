package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.query.model.ParticipantListResult;
import com.campusguinness.activity.application.query.model.ProjectParticipantListResult;
import com.campusguinness.activity.application.service.ActivityParticipantService;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.interfaces.web.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/school-admin/activities")
@PreAuthorize("hasRole('SCHOOL_ADMIN')")
public class SchoolAdminParticipantController {

    private final ActivityParticipantService participantService;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort membershipPort;

    public SchoolAdminParticipantController(ActivityParticipantService participantService,
                                             CurrentActor currentActor,
                                             SchoolMembershipQueryPort membershipPort) {
        this.participantService = participantService;
        this.currentActor = currentActor;
        this.membershipPort = membershipPort;
    }

    private UUID requireSchoolId() {
        return membershipPort.findActiveSchoolAdminSchoolId(currentActor.requireUserId())
                .orElseThrow(() -> new IllegalStateException("No active SCHOOL_ADMIN membership"));
    }

    private void requireOwnSchool(UUID activitySchoolId) {
        if (!requireSchoolId().equals(activitySchoolId)) {
            throw new IllegalArgumentException("Activity not found");
        }
    }

    // ── Activity Roster ──

    @GetMapping("/{activityId}/participants")
    public ResponseEntity<PageResponse<ParticipantItem>> listParticipants(
            @PathVariable UUID activityId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireOwnSchool(participantService.findActivity(activityId).schoolId());
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size < 1 || size > 100) throw new IllegalArgumentException("size must be between 1 and 100");
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.length() > 100) {
            throw new IllegalArgumentException("keyword must not exceed 100 characters");
        }
        var result = participantService.listParticipants(activityId, normalizedKeyword, page, size);
        var items = result.items().stream()
                .map(r -> new ParticipantItem(r.studentId(), r.displayName(), r.grade(),
                        r.className(), r.studentNumber(), r.assignedProjectCount(),
                        r.hasScoreAttempt(), r.joinedAt()))
                .toList();
        return ResponseEntity.ok(PageResponse.of(items, result.page(), result.size(), result.totalElements()));
    }

    @PostMapping("/{activityId}/participants")
    public ResponseEntity<ParticipantItem> addParticipant(@PathVariable UUID activityId,
                                                           @Valid @RequestBody AddParticipantRequest req) {
        var act = participantService.findActivity(activityId);
        requireOwnSchool(act.schoolId());
        var r = participantService.addParticipant(activityId, req.studentId());
        return ResponseEntity.created(URI.create("/api/v1/school-admin/activities/" + activityId + "/participants"))
                .body(new ParticipantItem(req.studentId(), null, null, null, null, 0, false, r.createdAt()));
    }

    @DeleteMapping("/{activityId}/participants/{studentId}")
    public ResponseEntity<Void> removeParticipant(@PathVariable UUID activityId,
                                                   @PathVariable UUID studentId) {
        requireOwnSchool(participantService.findActivity(activityId).schoolId());
        participantService.removeParticipant(activityId, studentId);
        return ResponseEntity.noContent().build();
    }

    // ── Project Assignment ──

    @GetMapping("/{activityId}/projects/{projectId}/participants")
    public List<ProjectParticipantItem> listProjectParticipants(@PathVariable UUID activityId,
                                                                  @PathVariable UUID projectId) {
        requireOwnSchool(participantService.findActivity(activityId).schoolId());
        return participantService.listProjectParticipants(activityId, projectId).stream()
                .map(ProjectParticipantItem::from)
                .toList();
    }

    @PostMapping("/{activityId}/projects/{projectId}/participants")
    public ResponseEntity<ProjectParticipantItem> assignToProject(@PathVariable UUID activityId,
                                                                    @PathVariable UUID projectId,
                                                                    @Valid @RequestBody AddParticipantRequest req) {
        requireOwnSchool(participantService.findActivity(activityId).schoolId());
        var r = participantService.assignToProject(activityId, projectId, req.studentId(),
                currentActor.requireUserId());
        return ResponseEntity.created(URI.create(
                "/api/v1/school-admin/activities/" + activityId + "/projects/" + projectId + "/participants"))
                .body(new ProjectParticipantItem(r.id(), r.activityProjectId(), r.activityParticipantId(),
                        req.studentId(), null, 0, false, null, null, null, false, r.assignedAt()));
    }

    @DeleteMapping("/{activityId}/projects/{projectId}/participants/{studentId}")
    public ResponseEntity<Void> unassignFromProject(@PathVariable UUID activityId,
                                                     @PathVariable UUID projectId,
                                                     @PathVariable UUID studentId) {
        requireOwnSchool(participantService.findActivity(activityId).schoolId());
        participantService.unassignFromProject(activityId, projectId, studentId);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ──

    public record AddParticipantRequest(@NotNull UUID studentId) {}
    public record ParticipantItem(UUID studentId, String displayName, String grade,
            String className, String studentNumber, long assignedProjectCount,
            boolean hasScoreAttempt, java.time.Instant joinedAt) {}

    public record ProjectParticipantItem(UUID activityProjectParticipantId, UUID activityProjectId,
            UUID participantId, UUID studentId, String displayName,
            int attemptCount, boolean hasScoreAttempt, UUID latestAttemptId,
            String latestAttemptStatus, String latestScoreValue, boolean hasApprovedScore,
            java.time.Instant assignedAt) {
        public static ProjectParticipantItem from(ProjectParticipantListResult r) {
            return new ProjectParticipantItem(r.activityProjectParticipantId(), r.activityProjectId(),
                    r.participantId(), r.studentId(), r.displayName(), r.attemptCount(),
                    r.hasScoreAttempt(), r.latestAttemptId(), r.latestAttemptStatus(),
                    r.latestScoreValue(), r.hasApprovedScore(), r.assignedAt());
        }
    }
}
