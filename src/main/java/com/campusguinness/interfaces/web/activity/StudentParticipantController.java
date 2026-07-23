package com.campusguinness.interfaces.web.activity;

import com.campusguinness.activity.application.port.ActivityParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectParticipantPort;
import com.campusguinness.activity.application.port.ActivityProjectPort;
import com.campusguinness.activity.application.port.ActivityRepository;
import com.campusguinness.activity.application.query.port.ActivityParticipantQueryPort;
import com.campusguinness.activity.internal.domain.ActivityId;
import com.campusguinness.identity.application.query.port.SchoolMembershipQueryPort;
import com.campusguinness.infrastructure.security.CurrentActor;
import com.campusguinness.score.application.port.ScoreAttemptRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentParticipantController {

    private final ActivityRepository activityRepo;
    private final ActivityProjectPort projectPort;
    private final ActivityParticipantPort participantPort;
    private final ActivityProjectParticipantPort projectParticipantPort;
    private final ActivityParticipantQueryPort participantQueryPort;
    private final ScoreAttemptRepository scoreAttemptRepo;
    private final CurrentActor currentActor;
    private final SchoolMembershipQueryPort membershipPort;

    public StudentParticipantController(ActivityRepository activityRepo,
                                         ActivityProjectPort projectPort,
                                         ActivityParticipantPort participantPort,
                                         ActivityProjectParticipantPort projectParticipantPort,
                                         ActivityParticipantQueryPort participantQueryPort,
                                         ScoreAttemptRepository scoreAttemptRepo,
                                         CurrentActor currentActor,
                                         SchoolMembershipQueryPort membershipPort) {
        this.activityRepo = activityRepo;
        this.projectPort = projectPort;
        this.participantPort = participantPort;
        this.projectParticipantPort = projectParticipantPort;
        this.participantQueryPort = participantQueryPort;
        this.scoreAttemptRepo = scoreAttemptRepo;
        this.currentActor = currentActor;
        this.membershipPort = membershipPort;
    }

    private UUID getMyUserId() {
        return currentActor.requireUserId();
    }

    private List<UUID> getMyMembershipIds() {
        return membershipPort.findActiveStudentMembershipIds(getMyUserId());
    }

    private List<ActivityParticipantPort.ParticipantRecord> getMyParticipants() {
        var membershipIds = getMyMembershipIds();
        if (membershipIds.isEmpty()) return List.of();
        return participantPort.findByMembershipIds(membershipIds);
    }

    // ── My Activities ──

    @GetMapping("/activities/mine")
    public List<MyActivityItem> listMyActivities() {
        var participants = getMyParticipants();
        if (participants.isEmpty()) return List.of();

        return participants.stream().map(p -> {
            var activity = activityRepo.findById(new ActivityId(p.activityId()));
            var projects = activity.map(a -> projectPort.findByActivity(a.id().value()).size()).orElse(0);
            var myProjects = projectParticipantPort.findByParticipantId(p.id()).size();

            return new MyActivityItem(
                    p.id(),
                    p.activityId(),
                    activity.map(a -> a.title()).orElse(null),
                    activity.map(a -> a.description()).orElse(null),
                    activity.map(a -> a.startTime()).orElse(null),
                    activity.map(a -> a.endTime()).orElse(null),
                    activity.map(a -> a.location()).orElse(null),
                    activity.map(a -> a.executionStatus().name()).orElse(null),
                    projects,
                    myProjects
            );
        }).toList();
    }

    @GetMapping("/activities/mine/{activityId}")
    public ResponseEntity<MyActivityItem> getMyActivity(@PathVariable UUID activityId) {
        var membershipIds = getMyMembershipIds();
        return participantQueryPort.findByActivityAndMemberships(activityId, membershipIds)
                .map(p -> {
                    var activity = activityRepo.findById(new ActivityId(activityId));
                    var projects = activity.map(a -> projectPort.findByActivity(a.id().value()).size()).orElse(0);
                    var myProjects = projectParticipantPort.findByParticipantId(p.participantId()).size();

                    return ResponseEntity.ok(new MyActivityItem(
                            p.participantId(), p.activityId(),
                            activity.map(a -> a.title()).orElse(null),
                            activity.map(a -> a.description()).orElse(null),
                            activity.map(a -> a.startTime()).orElse(null),
                            activity.map(a -> a.endTime()).orElse(null),
                            activity.map(a -> a.location()).orElse(null),
                            activity.map(a -> a.executionStatus().name()).orElse(null),
                            projects, myProjects));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── My Projects ──

    @GetMapping("/activity-projects/mine")
    public List<MyProjectItem> listMyProjects() {
        var participants = getMyParticipants();
        if (participants.isEmpty()) return List.of();

        var participantIds = participants.stream()
                .map(ActivityParticipantPort.ParticipantRecord::id).toList();
        var assignments = projectParticipantPort.findByParticipantIds(participantIds);

        // Collect all assignment data
        var activityProjectIds = assignments.stream()
                .map(ActivityProjectParticipantPort.ProjectParticipantRecord::activityProjectId)
                .distinct().toList();
        var apMap = activityProjectIds.stream()
                .map(id -> projectPort.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        ActivityProjectPort.ProjectRecord::id, ap -> ap));

        // Batch score attempts
        var studentId = getMyUserId();
        var scoreCounts = new HashMap<UUID, List<com.campusguinness.score.internal.domain.ScoreAttempt>>();
        for (var apId : activityProjectIds) {
            var scores = scoreAttemptRepo.findByActivityProjectIdAndStudentIds(apId, List.of(studentId));
            scoreCounts.put(apId, scores);
        }

        return assignments.stream().map(a -> {
            var apRecord = apMap.get(a.activityProjectId());
            var scores = scoreCounts.getOrDefault(a.activityProjectId(), List.of());
            int attemptCount = scores.size();
            boolean hasScoreAttempt = attemptCount > 0;
            boolean hasApproved = scores.stream()
                    .anyMatch(s -> s.status() == com.campusguinness.score.internal.domain.AttemptStatus.APPROVED);
            var latest = hasScoreAttempt ? scores.stream()
                    .sorted((s1, s2) -> {
                        var t1 = s1.submittedAt();
                        var t2 = s2.submittedAt();
                        if (t1 == null && t2 == null) return 0;
                        if (t1 == null) return 1;
                        if (t2 == null) return -1;
                        return t2.compareTo(t1);
                    }).findFirst().orElse(null) : null;

            return new MyProjectItem(
                    a.id(), a.activityProjectId(), a.activityParticipantId(),
                    apRecord != null ? apRecord.projectId() : null, null, null, null,
                    attemptCount,
                    latest != null ? latest.id().value() : null,
                    latest != null ? latest.status().name() : null,
                    hasApproved, a.assignedAt());
        }).toList();
    }

    @GetMapping("/activity-projects/mine/{activityProjectId}")
    public ResponseEntity<MyProjectItem> getMyProject(@PathVariable UUID activityProjectId) {
        var participants = getMyParticipants();
        var participantIds = participants.stream()
                .map(ActivityParticipantPort.ParticipantRecord::id).toList();

        return projectParticipantPort.findByParticipantIds(participantIds).stream()
                .filter(pp -> pp.activityProjectId().equals(activityProjectId))
                .findFirst()
                .map(pp -> {
                    var apRecord = projectPort.findById(activityProjectId).orElse(null);
                    var studentId = getMyUserId();
                    var scores = scoreAttemptRepo.findByActivityProjectIdAndStudentIds(
                            activityProjectId, List.of(studentId));
                    int attemptCount = scores.size();
                    boolean hasScoreAttempt = attemptCount > 0;
                    boolean hasApproved = scores.stream()
                            .anyMatch(s -> s.status() == com.campusguinness.score.internal.domain.AttemptStatus.APPROVED);
                    var latest = hasScoreAttempt ? scores.stream()
                            .sorted((s1, s2) -> {
                                var t1 = s1.submittedAt();
                                var t2 = s2.submittedAt();
                                if (t1 == null && t2 == null) return 0;
                                if (t1 == null) return 1;
                                if (t2 == null) return -1;
                                return t2.compareTo(t1);
                            }).findFirst().orElse(null) : null;

                    return ResponseEntity.ok(new MyProjectItem(
                            pp.id(), pp.activityProjectId(), pp.activityParticipantId(),
                            apRecord != null ? apRecord.projectId() : null, null, null, null,
                            attemptCount,
                            latest != null ? latest.id().value() : null,
                            latest != null ? latest.status().name() : null,
                            hasApproved, pp.assignedAt()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DTOs ──

    public record MyActivityItem(UUID activityParticipantId, UUID activityId, String title,
            String description, java.time.Instant startTime, java.time.Instant endTime,
            String location, String executionStatus, int projectCount, int assignedProjectCount) {}

    public record MyProjectItem(UUID projectParticipantId, UUID activityProjectId,
            UUID participantId, UUID projectId, String projectName, String scoreStorageType,
            String scoreUnit, int attemptCount, UUID latestAttemptId, String latestAttemptStatus,
            boolean hasApprovedScore, java.time.Instant assignedAt) {}
}
