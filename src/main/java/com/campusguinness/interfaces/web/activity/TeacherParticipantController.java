package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.port.ResponsibleTeacherPort;
import com.campusguinness.activity.application.query.model.ProjectParticipantListResult;
import com.campusguinness.activity.application.service.ActivityParticipantService;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherParticipantController {

    private final ActivityParticipantService participantService;
    private final ActivityRepository activityRepo;
    private final ActivityProjectPort projectPort;
    private final ResponsibleTeacherPort teacherPort;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort membershipPort;

    public TeacherParticipantController(ActivityParticipantService participantService,
                                         ActivityRepository activityRepo,
                                         ActivityProjectPort projectPort,
                                         ResponsibleTeacherPort teacherPort,
                                         CurrentActor currentActor,
                                         SchoolMembershipQueryPort membershipPort) {
        this.participantService = participantService;
        this.activityRepo = activityRepo;
        this.projectPort = projectPort;
        this.teacherPort = teacherPort;
        this.currentActor = currentActor;
        this.membershipPort = membershipPort;
    }

    private void requireResponsibleTeacher(UUID activityProjectId) {
        UUID teacherId = currentActor.requireUserId();
        var ap = projectPort.findById(activityProjectId)
                .orElseThrow(() -> new IllegalArgumentException("ActivityProject not found: " + activityProjectId));
        var activity = activityRepo.findById(new ActivityId(ap.activityId()))
                .orElseThrow(() -> new IllegalArgumentException("Activity not found"));

        // Check ACTIVE TEACHER membership
        UUID membershipId = membershipPort.findActiveTeacherMembershipId(teacherId, activity.schoolId())
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException(
                        "Not an active TEACHER at this school"));

        // Check responsible teacher assignment
        if (!teacherPort.exists(activityProjectId, membershipId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Not assigned as responsible teacher for this project");
        }
    }

    @GetMapping("/activity-projects/{activityProjectId}/participants")
    public List<TeacherProjectParticipantItem> listParticipants(@PathVariable UUID activityProjectId) {
        requireResponsibleTeacher(activityProjectId);

        var ap = projectPort.findById(activityProjectId).orElseThrow();
        var activity = activityRepo.findById(new ActivityId(ap.activityId())).orElseThrow();

        return participantService.listProjectParticipants(ap.activityId(), ap.projectId()).stream()
                .map(r -> new TeacherProjectParticipantItem(r.studentId(), r.displayName(),
                        r.attemptCount(), r.hasScoreAttempt(), r.latestAttemptId(),
                        r.latestAttemptStatus(), r.latestScoreValue(), r.hasApprovedScore(),
                        r.assignedAt()))
                .toList();
    }

    public record TeacherProjectParticipantItem(UUID studentId, String displayName,
            int attemptCount, boolean hasScoreAttempt, UUID latestAttemptId,
            String latestAttemptStatus, String latestScoreValue, boolean hasApprovedScore,
            java.time.Instant assignedAt) {}
}
