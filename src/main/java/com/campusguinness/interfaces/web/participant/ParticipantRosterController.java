package com.campusguinness.interfaces.web.participant;

import com.campusguinness.activity.application.service.ActivityParticipantRosterService;
import com.campusguinness.infrastructure.security.CurrentActor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ParticipantRosterController {
    private final ActivityParticipantRosterService service;
    private final CurrentActor currentActor;

    public ParticipantRosterController(ActivityParticipantRosterService service, CurrentActor currentActor) {
        this.service = service;
        this.currentActor = currentActor;
    }

    @GetMapping("/activities/{activityId}/participants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public List<ActivityParticipant> listActivity(@PathVariable UUID activityId) {
        return service.listActivityParticipants(activityId).stream()
                .map(p -> new ActivityParticipant(p.applicationId(), p.studentId())).toList();
    }

    @GetMapping("/activity-projects/{activityProjectId}/participants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public List<ProjectParticipant> listProject(@PathVariable UUID activityProjectId) {
        return service.listProjectParticipants(activityProjectId).stream()
                .map(p -> new ProjectParticipant(p.applicationId(), p.studentId(), p.attemptCount(),
                        p.hasScoreAttempt(), p.latestAttemptId(), p.latestAttemptStatus(),
                        p.latestScoreValue(), p.hasApprovedScore())).toList();
    }

    @PostMapping("/activity-projects/{activityProjectId}/participants")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<Void> assign(@PathVariable UUID activityProjectId,
                                        @RequestBody AssignRequest req) {
        service.assignParticipant(activityProjectId, req.applicationId(), currentActor.requireUserId());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/activity-projects/{activityProjectId}/participants/{applicationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public ResponseEntity<Void> unassign(@PathVariable UUID activityProjectId,
                                          @PathVariable UUID applicationId) {
        service.unassignParticipant(activityProjectId, applicationId);
        return ResponseEntity.noContent().build();
    }

    public record ActivityParticipant(UUID applicationId, UUID studentId) {}
    public record ProjectParticipant(UUID applicationId, UUID studentId, long attemptCount,
            boolean hasScoreAttempt, UUID latestAttemptId, String latestAttemptStatus,
            String latestScoreValue, boolean hasApprovedScore) {}
    public record AssignRequest(UUID applicationId) {}
}
